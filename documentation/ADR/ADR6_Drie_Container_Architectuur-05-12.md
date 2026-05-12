---
tags:
  - ADR
status: Accepted
deciders: 
- chris
- martijn
---
## Context and Problem Statement
De communicatiemodule bestaat uit meerdere functioneel gescheiden verantwoordelijkheden: het ontvangen en opslaan van afspraakdata, het plannen van notificaties op de juiste momenten, en het daadwerkelijk versturen van notificaties via externe providers. De vraag is hoe deze verantwoordelijkheden worden verdeeld over de applicatiecode en de deploymentinfrastructuur.

Een monolithische aanpak waarbij alle logica in één Spring Boot applicatie draait is technisch mogelijk maar introduceert schaalbaarheid en onderhoudbaarheidsrisico's. De notification worker is het meest belastinggevoelige onderdeel van het systeem: bij pieken in het aantal afspraken (bijvoorbeeld maandagochtend wanneer veel ziekenhuizen de week plannen) moet dit onderdeel meer berichten per tijdseenheid kunnen verwerken zonder de rest van het systeem te beïnvloeden.

---
## Considered Options
### Optie A: Monolithische Spring Boot applicatie
Alle functionaliteit draait in één Spring Boot applicatie in één Docker container.

Voordelen:
- Eenvoudiger te ontwikkelen en te debuggen
- Minder infrastructuurcomplexiteit
- Één deployment unit

Nadelen:
- Schalen van de notification worker vereist schalen van de volledige applicatie inclusief de inbound processor en scheduler, wat onnodige resource verspilling is
- Een crash in één component raakt de volledige applicatie
- Alle componenten moeten dezelfde taal, framework versie en dependencies gebruiken
- Moeilijker om individuele componenten onafhankelijk te updaten of te vervangen

### Optie B: Drie separate Spring Boot applicaties
De functionaliteit wordt verdeeld over drie zelfstandige Spring Boot applicaties, elk in hun eigen Docker container, die alleen communiceren via de gedeelde RabbitMQ queues en de gedeelde MariaDB database.

Container 1 is de inbound processor. Hij consumeert events van de externe RabbitMQ queue, valideert de data, en schrijft afspraken naar de database. Hij ontvangt ook cancellatie-webhooks van providers.

Container 2 is de scheduler. Hij leest periodiek de database, bepaalt welke notificaties verstuurd moeten worden op basis van de 24-uurs en 1-uurs vensters, en plaatst notificatiejobs op de interne RabbitMQ queue.

Container 3 is de notification worker. Hij consumeert notificatiejobs van de interne queue en roept de juiste provider adapter aan. Dit is de enige container die horizontaal schaalt.

Voordelen:
- Container 3 kan onafhankelijk horizontaal schalen zonder de andere containers te beïnvloeden
- Een crash in één container raakt de andere containers niet; de queues bufferen berichten totdat de container herstelt
- Elke container heeft één enkelvoudige verantwoordelijkheid wat de codebase overzichtelijk houdt
- Containers kunnen onafhankelijk gedeployed en geüpdatet worden
- Past direct bij het ontwerp dat in de workshoplessen is gepresenteerd
- Toekomstige uitbreidingen zoals een tweede type notificatie of een extra inbound bron kunnen als vierde container worden toegevoegd zonder de bestaande drie te wijzigen

Nadelen:
- Drie separate codebases die elk onderhouden moeten worden
- Meer infrastructuurcomplexiteit in Docker Compose
- Gedeelde database vereist zorgvuldig ontwerp om conflicten tussen containers te voorkomen
- Debugging over containersgrenzen heen is complexer dan binnen één applicatie

### Optie C: Modulaire monoliet met feature flags
Één Spring Boot applicatie met duidelijk gescheiden packages per verantwoordelijkheid, waarbij via configuratie bepaalde modules aan of uit gezet kunnen worden.

Voordelen:
- Eenvoudiger te ontwikkelen dan drie separate applicaties
- Minder infrastructuurcomplexiteit

Nadelen:
- Schalen blijft beperkt tot de volledige applicatie
- De scheiding tussen componenten is minder afdwingbaar dan bij aparte containers
- Niet uitbreidbaar naar de eindloze uitbreidbaarheid in beide richtingen die het drie-container model biedt

---
## Decision Outcome
**Gekozen: Optie B — Drie separate Spring Boot applicaties**

**Justification**
Het drie-container model is gekozen omdat het systeem in beide richtingen uitbreidbaar moet zijn: nieuwe inbound bronnen kunnen als extra producers op de externe queue worden aangesloten zonder container 1 te wijzigen, en nieuwe notificatietypes of providers kunnen worden toegevoegd zonder container 2 of 3 te wijzigen. Dit is precies de architectuur die in de workshoplessen is gepresenteerd als het model voor een systeem dat onbeperkt uitbreidbaar is in beide richtingen met een centrale kern.

Container 3 is de enige container die onder belasting schaalt. Door dit als een apart proces te isoleren kunnen meerdere instanties van container 3 als competing consumers op dezelfde `notification.queue` draaien zonder dat container 1 of container 2 hier iets van merkt. Dit is niet realiseerbaar in een monolithische aanpak zonder de volledige applicatie te schalen.

De gedeelde MariaDB database en de gedeelde RabbitMQ zijn de enige koppelpunten tussen de drie containers. Dit maakt de containers onderling vervangbaar: container 2 kan worden vervangen door een andere scheduler implementatie zolang hij dezelfde queue gebruikt en hetzelfde databaseschema leest.

---
## Consequences
Good, because:
- Container 3 schaalt horizontaal onafhankelijk van de andere containers
- Een crash in één container buffert berichten in de queue totdat de container herstelt, zonder dataverlies
- Het systeem is uitbreidbaar in beide richtingen zonder bestaande containers te wijzigen
- Elke container heeft één enkelvoudige verantwoordelijkheid en een overzichtelijke codebase

Neutraal, because:
- Drie codebases vereisen consistente afspraken over het databaseschema en het berichtformaat tussen containers
- Debugging over containergrenzen heen vereist gecentraliseerde logging en tracing via de observability stack

Bad, because:
- Meer infrastructuurcomplexiteit in Docker Compose dan een monolithische aanpak
- De gedeelde database is een potentieel bottleneck als alle drie containers tegelijkertijd hoge load genereren; dit wordt gemitigeerd door connection pooling en indexering op de meest gebruikte queries

---
## More information
Implementatieaandachtspunten:
- Elke container is een apart Maven project met zijn eigen pom.xml en main class
- De drie containers delen de MariaDB database en de RabbitMQ instantie via Docker Compose netwerkconfiguratie
- Container 3 kan worden geschaald via `docker compose up --scale notification-worker=3`
- Alle drie containers exporteren metrics via OpenTelemetry naar Prometheus en zijn zichtbaar in het gezamenlijke Grafana dashboard
- De Docker Compose health check volgorde is: MariaDB en RabbitMQ starten eerst, daarna container 1, daarna container 2, daarna container 3
- Zie ADR 4 voor het queue-ontwerp dat de communicatie tussen de containers mogelijk maakt