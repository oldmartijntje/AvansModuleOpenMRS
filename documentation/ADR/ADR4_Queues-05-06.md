---
status: Accepted
date: 2026-05-06
deciders: 
- angel
- chris
- martijn
tags:
  - ADR
---
# AD: Hoe gebruiken wij queues?
## Context and Problem Statement
De communicatiemodule bestaat uit drie zelfstandige Spring Boot applicaties die elk in hun eigen Docker container draaien en alleen via gedeelde infrastructuur met elkaar communiceren. De vraag is hoe de asynchrone communicatie tussen deze containers en tussen de OpenMRS plugins en de inbound processor wordt ingericht.

Er zijn twee afzonderlijke queue-lagen in het systeem. De externe queue verbindt de OpenMRS plugins met de inbound processor (container 1). De interne queue verbindt de scheduler (container 2) met de notification worker (container 3). Beide lagen hebben verschillende producenten, consumers en betrouwbaarheidseisen.

---
## Considered Options
### Optie A: In-memory queue binnen de applicatie
Gebruik maken van een Java BlockingQueue of Spring's interne ApplicationEventPublisher zonder externe broker.

Voordelen:
- Geen extra infrastructuur nodig
- Eenvoudig te implementeren

Nadelen:
- Berichten gaan verloren bij herstart of crash
- Werkt niet tussen separate containers
- Geen dead-letter mechanisme
- Onaanvaardbaar voor medische data

### Optie B: RabbitMQ met Spring AMQP voor beide queue-lagen
Beide queue-lagen gebruiken RabbitMQ als broker. De externe queue verbindt OpenMRS plugins met container 1 via een topic exchange. De interne queue verbindt container 2 met container 3 via een directe queue. Spring AMQP biedt de abstractielaag in alle drie Spring Boot applicaties.

Voordelen:
- Één technologie voor beide queue-lagen, minder operationele complexiteit
- Bewezen technologie met uitgebreide Spring Boot integratie via Spring AMQP
- Ingebouwde ondersteuning voor dead-letter queues, ACK/NACK en durable queues
- Berichten gaan niet verloren bij herstart als queues durable zijn geconfigureerd
- Competing consumers mogelijk voor horizontale schaalbaarheid van container 3
- Beschikbaar als officiële Docker image met management UI

Nadelen:
- Extra infrastructuurcomponent die beheerd moet worden
- Vereist correcte configuratie van durable queues en ACK om berichtenverlies te voorkomen
- Retry storms mogelijk bij grootschalige uitval als backoff niet correct geconfigureerd is

### Optie C: Kafka voor de externe queue, RabbitMQ voor de interne queue
Kafka handelt de hoge-volume externe stroom van OpenMRS events af. RabbitMQ handelt de interne notificatiejobs af.

Voordelen:
- Kafka is beter geschikt voor hoge-volume event streams met meerdere producers
- Events blijven beschikbaar na verwerking voor replay

Nadelen:
- Twee verschillende messaging technologieën verhogen de operationele en ontwikkelcomplexiteit aanzienlijk
- Kafka is overkill voor het berichtenvolume van een afsprakensysteem
- Vereist meer operationele kennis dan beschikbaar binnen het team

---
## Decision Outcome
**Gekozen: Optie B — RabbitMQ met Spring AMQP voor beide queue-lagen**

**Justification**
Één messaging technologie voor beide queue-lagen reduceert de operationele complexiteit. RabbitMQ met durable queues en expliciete ACK garandeert dat berichten niet verloren gaan bij een crash van een van de drie containers. Kafka biedt meer functionaliteit dan nodig is voor het berichtenvolume van dit systeem en zou de complexiteit onnodig verhogen.

Spring AMQP als abstractielaag voorkomt dat de applicatiecode direct afhankelijk is van RabbitMQ-specifieke implementatiedetails, en sluit aan op het principe uit de lessen om te programmeren tegen een interface in plaats van een implementatie. Alle drie Spring Boot applicaties gebruiken dezelfde Spring AMQP configuratiepatronen, wat de codebases consistent houdt.

---
## Queue-ontwerp
**Externe queue-laag (OpenMRS plugins naar container 1)**
De topic exchange heet `appointment.exchange`. Alle OpenMRS plugins publiceren hier naartoe met een routing key volgens het patroon `appointment.{tenantId}.{eventType}`, bijvoorbeeld `appointment.hospital-amsterdam.CREATED` of `appointment.hospital-nairobi.CANCELLED`.

De inbound queue `appointment.inbound` is gebonden aan het patroon `appointment.#` en ontvangt alle binnenkomende appointment events van alle tenants. Container 1 consumeert exclusief van deze queue, valideert het event, en schrijft de afspraakdata naar de gedeelde MariaDB database.

**Interne queue-laag (container 2 naar container 3)**
De directe queue `notification.queue` ontvangt notificatiejobs van container 2 op de geplande verzendmomenten (24 uur en 1 uur voor de afspraak). Container 3 consumeert van deze queue en roept de juiste provider adapter aan. Omdat container 3 horizontaal schaalt (meerdere instanties mogelijk) gebruiken alle instanties competing consumers op dezelfde queue.

De dead-letter queue `notification.dlq` ontvangt berichten die na drie verwerkingspogingen door container 3 nog steeds falen. Beide interne queues zijn durable geconfigureerd.

**Cancellatie-queue**
De directe queue `cancellation.queue` ontvangt annuleringsverzoeken die binnenkomen via de provider webhook op container 1. Container 1 plaatst het verzoek op deze queue waarna het asynchroon wordt verwerkt: de OpenMRS REST API wordt aangeroepen om de afspraak te annuleren aan de OpenMRS-kant, waarna de bevestiging terugkomt via de externe queue als een CANCELLED event.

**ACK en retry gedrag**
Elke container bevestigt een bericht pas met ACK nadat de verwerking succesvol is afgerond. Bij een mislukte verwerking wordt NACK gestuurd en wordt het bericht met exponential backoff opnieuw aangeboden, maximaal drie keer. Na drie pogingen verplaatst RabbitMQ het bericht automatisch naar `notification.dlq`.

---
## Consequences
Good, because:
- Berichten gaan niet verloren bij een crash of herstart van een van de drie containers
- De drie containers zijn volledig ontkoppeld in tijd en gedrag en communiceren alleen via de queues en de gedeelde database
- Container 3 kan horizontaal schalen door meerdere instanties als competing consumers op `notification.queue` te zetten
- Één messaging technologie voor beide queue-lagen houdt de operationele complexiteit beheersbaar

Neutraal, because:
- De routing key conventie van de externe queue moet consistent zijn tussen de OpenMRS plugin en container 1; dit wordt gedocumenteerd in de technische beheerdershandleiding
- Alle drie containers moeten idempotent zijn: hetzelfde bericht twee keer verwerken mag niet leiden tot dubbele acties

Bad, because:
- Als de externe RabbitMQ tijdelijk niet bereikbaar is vanuit een OpenMRS-instantie kunnen events verloren gaan; de plugin implementeert retry-logica maar kan geen onbeperkte hoeveelheid events bufferen
- Bij een storing waarbij alle berichten tegelijk opnieuw worden aangeboden bestaat het risico op een retry storm; dit wordt gemitigeerd door exponential backoff in alle consumers
- RabbitMQ is een gedeeld infrastructuurcomponent waarvan alle drie containers afhankelijk zijn; downtime van RabbitMQ raakt het volledige systeem
---
## More information
Implementatieaandachtspunten:
- RabbitMQ draait als één Docker container op de centrale VPS met de management plugin voor monitoring via de web UI op poort 15672
- De topic exchange en alle queues worden gedeclareerd via `@Bean` configuratie in Spring AMQP met `ExchangeBuilder`, `QueueBuilder.durable()` en `BindingBuilder`
- Container 1 gebruikt `@RabbitListener` op `appointment.inbound` en `cancellation.queue`
- Container 2 gebruikt `RabbitTemplate` om jobs te publiceren naar `notification.queue`
- Container 3 gebruikt `@RabbitListener` op `notification.queue`
- De dead-letter queue wordt gekoppeld via een `x-dead-letter-exchange` argument op `notification.queue`
- Alle drie containers starten pas als RabbitMQ de health check heeft doorstaan via `depends_on` met `condition: service_healthy` in Docker Compose
- Zie [[ADR3_Integratiemethode-05-06|ADR 3]] voor de motivatie achter de event-driven integratie met OpenMRS
- Zie [[ADR5_OpenMRS_Plugin-05-11|ADR 5]] voor de plugin architectuur die berichten publiceert naar de externe queue
- Zie ADR 6 voor de drie-container architectuur beslissing
- Zie ADR 8 voor de beveiligingseisen rondom de RabbitMQ infrastructuur