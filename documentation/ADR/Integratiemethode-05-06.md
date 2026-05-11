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

Als onderdeel van het onderzoek is de OpenMRS referentie-applicatie lokaal opgestart via Docker en zijn de beschikbare API-mogelijkheden systematisch onderzocht. De FHIR2 module bleek het `Appointment` resource type niet te ondersteunen in de geïnstalleerde versie. De standaard REST API vereist een UUID voor individuele opzoekingen en biedt geen lijst- of zoekfunctionaliteit. De Bahmni Appointment Scheduling module bleek wel geïnstalleerd te zijn en biedt een werkende zoekendpoint op basis van een datumbereik.

Er zijn drie fundamenteel verschillende benaderingen onderzocht: de module vraagt zelf periodiek data op bij OpenMRS (polling), OpenMRS stuurt data naar de module wanneer er iets verandert (webhook/push), of OpenMRS publiceert events op een gedeelde berichtenbus waar de module op luistert (event streaming).

---
## Considered Options

### Optie A: Polling via de Bahmni Appointments REST API
De communicatiemodule bevraagt periodiek de OpenMRS REST API om aankomende afspraken op te halen. De scheduler draait elke minuut en vraagt alle afspraken op binnen een tijdvenster van nu tot 25 uur vooruit.

Voordelen:
- Werkend en bevestigd via hands-on onderzoek op de echte OpenMRS installatie
- Geen installatie of configuratie vereist aan OpenMRS-kant buiten de reeds aanwezige Bahmni module
- Bij downtime van de module gaan er geen events verloren: bij herstel wordt gewoon opnieuw gepolled
- Schaalt goed naar meerdere OpenMRS-instanties: elke tenant krijgt een volledig onafhankelijke polling-configuratie

Nadelen:
- Polling introduceert een kleine vertraging (tot 1 minuut) tussen afspraakaanmaak en detectie
- Bij veel instanties groeit het aantal uitgaande API-calls lineair
- OpenMRS moet bereikbaar zijn op het moment van polling: tijdelijke downtime betekent dat de module even blind is
- Vraagt om goede idempotentie-logica zodat afspraken niet dubbel worden verwerkt
- De response bevat geen telefoonnummer van de patiënt, wat een tweede API-call vereist naar `GET /openmrs/ws/rest/v1/patient/{uuid}`

### Optie B: Webhooks vanuit OpenMRS
OpenMRS stuurt een HTTP POST naar de communicatiemodule zodra een afspraak wordt aangemaakt, gewijzigd of geannuleerd. OpenMRS heeft hiervoor de REST Hook module beschikbaar.

Voordelen:
- Real-time: de module ontvangt data onmiddellijk bij een wijziging
- Geen onnodige API-calls als er weinig afspraken zijn
- Duidelijk event-driven model

Nadelen:
- Vereist installatie en configuratie van de REST Hook module aan OpenMRS-kant, bij elke organisatie apart
- Als de communicatiemodule tijdelijk down is op het moment dat OpenMRS een webhook verstuurt, gaat het event verloren tenzij OpenMRS retry-logica heeft (wat niet standaard het geval is)
- Elke OpenMRS-instantie moet weten naar welk endpoint hij moet pushen, wat configuratie per tenant vereist
- Moeilijker te debuggen: fouten aan de ontvangende kant zijn minder zichtbaar voor de OpenMRS-beheerder

### Optie C: Event streaming via een gedeelde berichtenbus (AtomFeed)
OpenMRS heeft ingebouwde ondersteuning voor AtomFeed, een publish/subscribe mechanisme waarbij OpenMRS events publiceert als Atom-entries. De communicatiemodule leest deze feed en verwerkt nieuwe entries.

Voordelen:
- Events gaan niet verloren: de feed bewaart events en de module houdt bij welke al verwerkt zijn
- Asynchroon van aard: geen directe afhankelijkheid tussen OpenMRS-uptime en module-uptime
- Breed ondersteund in de OpenMRS-community

Nadelen:
- Vereist installatie van de AtomFeed module aan OpenMRS-kant
- De feed is niet direct gekoppeld aan de Bahmni Appointments module; mapping van het datamodel is handmatig werk
- Complexere infrastructuur dan polling
- Minder geschikt voor het SaaS-model: de module moet per tenant een aparte feed-positie bijhouden en de feed-URL kennen

---
## Decision Outcome
**Gekozen: Optie A, Polling via de Bahmni Appointments REST API**

**Justification**
Tijdens praktisch onderzoek op de OpenMRS referentie-applicatie is vastgesteld dat het FHIR `Appointment` resource type niet beschikbaar is in de geïnstalleerde versie. De Bahmni Appointment Scheduling module biedt daarentegen een werkende REST API met datumbereik-filtering, bevestigd via directe API-aanroepen op de lokale installatie.

Webhooks lijken aantrekkelijk vanwege de real-time aard, maar het verlies van events bij downtime is in een medische context onaanvaardbaar zonder aanvullende retry-infrastructuur aan OpenMRS-kant die we niet kunnen garanderen. AtomFeed lost dit op maar introduceert installatieverplichtingen bij elke klant en een losstaand datamodel dat handmatig gekoppeld moet worden aan de Bahmni appointmentstructuur.

Polling via de Bahmni REST API biedt het beste evenwicht: de module werkt met wat er daadwerkelijk beschikbaar is op de OpenMRS installatie, er is geen extra installatie nodig aan OpenMRS-kant, en het systeem is robuust bij downtime aan beide kanten. Daarnaast schaalt de aanpak horizontaal: elke tenant is volledig onafhankelijk geconfigureerd, waardoor het toevoegen van een nieuwe OpenMRS-instantie geen impact heeft op bestaande tenants. Het nadeel van polling-latency is in dit domein acceptabel: notificaties worden 24 uur en 1 uur voor de afspraak verstuurd, waarbij een vertraging van maximaal 1 minuut geen klinisch verschil maakt.

---
## Consequences
Good, because:
- De Bahmni Appointments API is bevestigd werkend op de echte OpenMRS installatie via hands-on onderzoek
- Bij downtime van de module gaan geen events verloren; bij herstel wordt de volgende polling-cyclus gewoon uitgevoerd
- Nieuwe OpenMRS-instanties toevoegen is een kwestie van een endpoint en credentials registreren, zonder aanpassingen aan de OpenMRS-installatie zelf

Neutraal, because:
- De module moet idempotentie goed implementeren zodat een afspraak die al in de wachtrij staat niet opnieuw wordt ingepland bij de volgende poll
- Het polling-interval moet zorgvuldig gekozen worden: te kort verhoogt de load op OpenMRS, te lang vergroot de kans op late detectie bij afspraken die kort van tevoren worden aangemaakt
- Voor elke afspraak is een tweede API-call nodig om het telefoonnummer van de patiënt op te halen; dit verhoogt het aantal requests maar is onvermijdbaar gegeven de structuur van de API

Bad, because:
- Bij downtime van OpenMRS ziet de module tijdelijk geen nieuwe of gewijzigde afspraken; afspraken die tijdens die downtime worden aangemaakt kunnen hun 24-uurs notificatie missen als de downtime lang genoeg duurt
- Het aantal uitgaande API-calls groeit met het aantal tenants; bij honderden instanties is rate limiting en request-spreiding noodzakelijk

---
## More information
Implementatieaandachtspunten:
- De module gebruikt `POST /openmrs/ws/rest/v1/appointments/search` met een JSON body `{"startDate": "[now]", "endDate": "[now+25h]"}` om aankomende afspraken op te halen
- Tijdstempels in de response zijn Unix milliseconden en worden geconverteerd naar `ZonedDateTime` rekening houdend met de tijdzone van de betreffende tenant
- Voor het ophalen van het telefoonnummer van de patiënt wordt een tweede call gedaan naar `GET /openmrs/ws/rest/v1/patient/{uuid}`
- De poller haalt bij elke gevonden afspraak direct ook de patiëntgegevens op en assembleert een volledig notificatiebericht. Dit bericht wordt als fat event op de RabbitMQ queue geplaatst zodat de notificatieworker geen aanvullende aanroepen naar OpenMRS hoeft te doen tijdens de verwerking. Dit voorkomt dat de worker temporeel gekoppeld raakt aan de beschikbaarheid van OpenMRS op het moment van notificatieverzending. Zie ook ADR 4.
- Per tenant wordt de laatste succesvolle poll-timestamp opgeslagen zodat gemiste polls gedetecteerd kunnen worden
- Authenticatie richting OpenMRS verloopt via Basic Auth met een per-tenant gebruikersnaam en wachtwoord die versleuteld worden opgeslagen
- De Bahmni Appointment Scheduling module moet geïnstalleerd zijn op de OpenMRS-instantie; dit wordt geverifieerd bij onboarding van een nieuwe tenant