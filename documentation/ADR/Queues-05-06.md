---
status: Accepted
date: 2026-05-06
deciders: angel, chris, martijn
---
## Context and Problem Statement
De communicatiemodule moet afspraken ophalen bij OpenMRS, notificaties plannen op twee vaste momenten (24 uur en 1 uur voor de afspraak), en die notificaties asynchroon verwerken via een externe messaging provider. De vraag is hoe de communicatie tussen de poller, de scheduler en de notificatieworker wordt ingericht, en welke queuing infrastructuur daarvoor het meest geschikt is.

Directe synchrone aanroepen tussen deze componenten zijn ongeschikt: als een provider tijdelijk niet beschikbaar is, mag de scheduler niet blokkeren. Als de worker vastloopt, mogen er geen notificaties verloren gaan. De componenten moeten onafhankelijk van elkaar kunnen draaien en falen.

---
## Considered Options
### Optie A: RabbitMQ met Spring AMQP
RabbitMQ is een volwassen AMQP-broker die point-to-point messaging en publish/subscribe ondersteunt. Spring AMQP biedt een abstractielaag die directe werking met bytes, verbindingen en kanalen verbergt achter annotaties en templates.

Voordelen:
- Bewezen technologie met uitgebreide Spring Boot integratie via Spring AMQP
- Ingebouwde ondersteuning voor dead-letter queues, ACK/NACK en durable queues
- Berichten gaan niet verloren bij herstart van de applicatie als queues durable zijn geconfigureerd
- Competing consumers mogelijk voor horizontale schaalbaarheid van de worker
- Beschikbaar als officiële Docker image met management UI voor monitoring

Nadelen:
- Extra infrastructuurcomponent die beheerd moet worden
- Vereist correcte configuratie van durable queues en ACK om berichtenverlies te voorkomen
- Retry storms mogelijk bij grootschalige uitval als backoff niet correct is geconfigureerd

**Optie B: In-memory queue binnen de applicatie**
Gebruik maken van een Java `BlockingQueue` of Spring's interne `ApplicationEventPublisher` zonder externe broker.

Voordelen:
- Geen extra infrastructuur nodig
- Eenvoudiger te implementeren en te debuggen

Nadelen:
- Berichten gaan verloren bij herstart of crash van de applicatie
- Niet schaalbaar naar meerdere instanties van de worker
- Voldoet niet aan de niet-functionele eis voor betrouwbare berichtverwerking
- Geen dead-letter mechanisme beschikbaar

---
## Decision Outcome
**Gekozen: Optie A, RabbitMQ met Spring AMQP**

**Justification**
Een in-memory queue is onaanvaardbaar in een medische context omdat berichten verloren gaan bij een applicatiecrash. RabbitMQ met durable queues en expliciete ACK garandeert dat een notificatiejob niet verdwijnt zolang de worker het bericht niet bevestigt.

De keuze voor Spring AMQP als abstractielaag sluit aan op de aanbeveling om niet direct met de onderliggende AMQP-infrastructuur te werken maar via een abstractie die het werken met queues vereenvoudigt en past bij de Java tech stack die voor het project is gekozen.

Voor de berichtstructuur is gekozen voor een fat event: het notificatiebericht op de queue bevat alle benodigde gegevens, waaronder patiëntgegevens inclusief telefoonnummer, afspraakdetails, tenant-informatie en het geplande tijdstip van verzending. Dit voorkomt dat de worker een extra synchrone aanroep naar OpenMRS moet doen op het moment van verwerking, wat temporele koppeling zou herintroduceren en de worker afhankelijk zou maken van de beschikbaarheid van OpenMRS. Zie ook ADR 3.

---
## Queue-ontwerp
De module gebruikt twee queues. De hoofdqueue `notification.queue` ontvangt notificatiejobs van de scheduler. De dead-letter queue `notification.dlq` ontvangt berichten die na drie verwerkingspogingen nog steeds falen. Beide queues zijn durable geconfigureerd zodat berichten een herstart overleven.

De worker bevestigt een bericht pas met ACK nadat de provider-aanroep succesvol is afgerond. Bij een mislukte aanroep wordt NACK gestuurd en wordt het bericht met exponential backoff opnieuw aangeboden, maximaal drie keer. Na drie pogingen verplaatst RabbitMQ het bericht automatisch naar de dead-letter queue voor handmatige inspectie.

---
## Consequences
Good, because:
- Notificatiejobs gaan niet verloren bij een crash of herstart van de applicatie
- De poller en worker zijn volledig ontkoppeld in tijd en gedrag: de worker hoeft niet te draaien terwijl de poller afspraken ophaalt
- De worker kan horizontaal schalen door meerdere consumers op dezelfde queue te zetten

Neutraal, because:
- De worker moet idempotent zijn: hetzelfde bericht twee keer verwerken mag niet leiden tot twee verstuurde notificaties
- De fat event keuze betekent dat de berichtgrootte groter is dan bij een thin event, maar dit is verwaarloosbaar voor dit gebruik

Bad, because:
- Bij een storing waarbij alle berichten tegelijk opnieuw worden aangeboden bestaat het risico op een retry storm; dit wordt gemitigeerd door exponential backoff in de worker
- RabbitMQ is een extra component in Docker Compose die correct geconfigureerd en gemonitord moet worden
---
## More information
Implementatieaandachtspunten:
- RabbitMQ draait als container in Docker Compose met de management plugin voor monitoring via de web UI op poort 15672
- Spring AMQP wordt geconfigureerd via `@RabbitListener` op de worker en `RabbitTemplate` in de scheduler
- Durable queues worden gedeclareerd via `@Bean` configuratie met `QueueBuilder.durable()`
- De dead-letter queue wordt gekoppeld via een `x-dead-letter-exchange` argument op de hoofdqueue
- De applicatie start pas als RabbitMQ de health check heeft doorstaan via `depends_on` met `condition: service_healthy` in Docker Compose
- Een notificatiebericht op de queue bevat minimaal: tenant UUID, afspraak UUID, patiëntnaam, telefoonnummer, afspraakdatum en tijd, servicenaam, geplande verzenddatum (24h of 1h variant), en de te gebruiken messaging provider voor die tenant