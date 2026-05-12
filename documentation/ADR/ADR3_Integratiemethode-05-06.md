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
# AD: Hoe koppelt de communicatiemodule aan OpenMRS?

## Context and Problem Statement
De communicatiemodule heeft afspraakdata nodig van OpenMRS om notificaties te kunnen versturen. De vraag is via welk mechanisme deze data bij de module terechtkomt. Dit is geen triviale keuze: de module moet meerdere OpenMRS-instanties bedienen, moet bestand zijn tegen downtime aan beide kanten, en moet aansluiten op de integratiemogelijkheden die OpenMRS daadwerkelijk biedt.

Als onderdeel van het onderzoek is de OpenMRS referentie-applicatie lokaal opgestart via Docker en zijn de beschikbare API-mogelijkheden systematisch onderzocht. De FHIR2 module bleek het Appointment resource type niet te ondersteunen in de geïnstalleerde versie. De standaard REST API vereist een UUID voor individuele opzoekingen en biedt geen lijst- of zoekfunctionaliteit. De Bahmni Appointment Scheduling module bleek wel geïnstalleerd en biedt een werkende zoekendpoint op basis van datumbereik. Polling via deze API was aanvankelijk de gekozen aanpak, maar na verdere analyse is besloten over te stappen op een event-driven push model via een zelfgebouwde OpenMRS plugin. Dit biedt betere ontkoppeling, directe verwerking van annuleringen, en elimineert de noodzaak voor periodieke API-aanroepen.

Er zijn vier benaderingen onderzocht.

---
## Considered Options

### Optie A: Polling via de Bahmni Appointments REST API
De communicatiemodule bevraagt periodiek de OpenMRS REST API om aankomende afspraken op te halen via `POST /openmrs/ws/rest/v1/appointments/search`.

Voordelen:
- Werkend en bevestigd via hands-on onderzoek op de echte OpenMRS installatie
- Geen extra installatie aan OpenMRS-kant buiten de reeds aanwezige Bahmni module
- Bij downtime van de inbound processor gaan er geen events verloren: bij herstel wordt opnieuw gepolled

Nadelen:
- Polling introduceert een vertraging van tot 1 minuut tussen afspraakaanmaak en detectie
- Bij veel instanties groeit het aantal uitgaande API-calls lineair
- Annuleringen zijn lastig af te handelen: een afspraak die al in de interne queue zit kan alsnog verstuurd worden als de annulering niet tijdig wordt gedetecteerd
- Vraagt om complexe idempotentie-logica en extra status-check calls bij elke verwerkingsstap

### Optie B: Webhooks vanuit OpenMRS
OpenMRS stuurt een HTTP POST naar de inbound processor zodra een afspraak wordt aangemaakt, gewijzigd of geannuleerd via de REST Hook module.

Voordelen:
- Real-time: de inbound processor ontvangt data onmiddellijk bij een wijziging
- Geen onnodige API-calls als er weinig afspraken zijn

Nadelen:
- Vereist installatie en configuratie van de REST Hook module aan OpenMRS-kant bij elke organisatie apart
- Als de inbound processor tijdelijk down is gaat het event verloren tenzij OpenMRS retry-logica heeft, wat niet standaard het geval is
- Elke OpenMRS-instantie moet weten naar welk endpoint hij moet pushen, wat configuratie per tenant vereist

### Optie C: Event streaming via AtomFeed
OpenMRS publiceert events als Atom-entries via de AtomFeed module. De inbound processor leest deze feed en verwerkt nieuwe entries.

Voordelen:
- Events gaan niet verloren: de feed bewaart events en de processor houdt bij welke al verwerkt zijn
- Asynchroon van aard: geen directe afhankelijkheid tussen OpenMRS-uptime en processor-uptime

Nadelen:
- Vereist installatie van de AtomFeed module aan OpenMRS-kant
- De feed is niet direct gekoppeld aan de Bahmni Appointments module; mapping van het datamodel is handmatig werk
- Minder geschikt voor het SaaS-model: de processor moet per tenant een aparte feed-positie bijhouden

### Optie D: Event-driven push via een zelfgebouwde OpenMRS plugin
Per OpenMRS-instantie wordt een kleine plugin geïnstalleerd die luistert naar appointment events via de bestaande OpenMRS Event Module. Wanneer een afspraak wordt aangemaakt, gewijzigd of geannuleerd publiceert de plugin een bericht naar een gedeelde externe RabbitMQ exchange op de centrale VPS. De inbound processor (container 1) consumeert deze berichten, slaat de afspraakdata op in de gedeelde MariaDB database, en maakt de data beschikbaar voor de scheduler (container 2) en de notification worker (container 3).

Voordelen:
- Real-time: events worden direct bij aanmaak of wijziging doorgestuurd zonder polling-lag
- Annuleringen worden direct als CANCELLED event ontvangen en verwerkt in de lokale database, waardoor geen extra status-checks nodig zijn bij notificatieverwerking
- De inbound processor hoeft na ontvangst van een event nooit meer terug te vragen aan OpenMRS voor normale notificatieverwerking
- Meerdere OpenMRS-instanties kunnen tegelijkertijd publiceren naar dezelfde RabbitMQ exchange zonder elkaar te beïnvloeden
- De plugin is klein en zelfstandig: hij heeft één enkelvoudige verantwoordelijkheid en maakt gebruik van de reeds beschikbare OpenMRS Event Module als trigger

Nadelen:
- Vereist installatie van de plugin bij elke OpenMRS-instantie
- Als de externe RabbitMQ tijdelijk niet bereikbaar is vanuit een OpenMRS-instantie kunnen events verloren gaan tenzij de plugin retry-logica implementeert
- Voegt een extra codebase toe aan het project naast de drie Spring Boot applicaties

---
## Decision Outcome
**Gekozen: Optie D, Event-driven push via een zelfgebouwde OpenMRS pluginI**

**Justification**
Polling was aanvankelijk de gekozen aanpak op basis van hands-on onderzoek op de OpenMRS installatie. Na verdere analyse bleek echter dat polling een fundamenteel probleem heeft met annuleringen: een afspraak die al als notificatiejob in de interne queue staat kan worden verstuurd terwijl de afspraak inmiddels is geannuleerd. Dit vereist extra synchrone status-check calls naar OpenMRS op het moment van verwerking, wat temporele koppeling herintroduceert.

Webhooks lossen de real-time eis op maar zijn onbetrouwbaar bij downtime van de inbound processor omdat events dan definitief verloren gaan. AtomFeed bewaart events maar vereist extra installatie en een losstaand datamodel.

De plugin-aanpak combineert de voordelen van real-time events met een betrouwbare berichteninfrastructuur. De inbound processor ontvangt elk event inclusief annuleringen direct op het moment dat het plaatsvindt, slaat dit op in de gedeelde database, en maakt de data beschikbaar voor de rest van de pipeline zonder ooit terug te hoeven vragen aan OpenMRS. Dit is de meest volledige ontkoppeling van de vier opties en past het beste bij de drie-container architectuur die is gekozen in ADR 6.

De plugin identificeert de tenant via een OpenMRS global property genaamd `communicatie.tenantId` die door de hospital IT-beheerder wordt ingesteld bij onboarding. De externe RabbitMQ draait als Docker container op de centrale VPS en is bereikbaar voor alle OpenMRS-instanties via de global property `communicatie.rabbitmq.url`

---
## Consequences
Good, because:
- Annuleringen worden direct en correct verwerkt zonder extra API-calls of race conditions
- De inbound processor is na ontvangst van het initiële event volledig onafhankelijk van de beschikbaarheid van OpenMRS
- Meerdere tenants kunnen tegelijkertijd events publiceren zonder interferentie
- De gedeelde MariaDB database is de single source of truth voor alle afspraakdata die door alle drie containers wordt gebruikt

Neutraal, because:
- De plugin moet bij elke nieuwe tenant worden geïnstalleerd en geconfigureerd; dit is gedocumenteerd als onderdeel van de onboarding procedure voor technisch beheerders
- De plugin voegt een derde codebase toe aan het project naast de drie Spring Boot applicaties, maar heeft een enkelvoudige verantwoordelijkheid en een minimale omvang

Bad, because:
- Als de externe RabbitMQ tijdelijk niet bereikbaar is vanuit een OpenMRS-instantie kunnen events verloren gaan; dit wordt gemitigeerd door retry-logica in de plugin en monitoring op de RabbitMQ verbinding
- De communicatiemodule is afhankelijk van correcte installatie en configuratie van de plugin aan OpenMRS-kant; foutieve configuratie van de tenantId of RabbitMQ URL leidt tot onherkenbare of verloren berichten

---
## More information
Implementatieaandachtspunten:
- De plugin wordt gebouwd als een standaard OpenMRS OMOD bestand en maakt gebruik van de OpenMRS Event Module als trigger voor appointment events via de Bahmni Appointment Scheduling module
- De plugin luistert op drie event types: CREATED, UPDATED en VOIDED/CANCELLED
- Elk gepubliceerd bericht bevat minimaal: tenantId, appointmentUuid, patientUuid, eventType, startDateTime, endDateTime, serviceName, patientName, phoneNumber en een timestamp van het event
- De tenantId wordt gelezen uit de OpenMRS global property `communicatie.tenantId`
- De RabbitMQ verbindingsURL wordt gelezen uit de OpenMRS global property `communicatie.rabbitmq.url`
- De externe RabbitMQ draait als Docker container op de centrale VPS en is bereikbaar op poort 5671 via TLS
- De inbound processor (container 1) consumeert van de externe RabbitMQ queue en schrijft naar de gedeelde MariaDB database
- Zie [[ADR5_OpenMRS_Plugin-05-11|ADR 5]] voor de volledige plugin architectuur en implementatiedetails
- Zie [[ADR4_Queues-05-06|ADR 4]] voor het volledige queue en exchange ontwerp
- Zie [[ADR6_Drie_Container_Architectuur-05-12|ADR 6]] voor de drie-container architectuur beslissing