---
status: Reviewing
date: 2026-05-06
deciders: angel, chris, martijn
---

# AD: Hoe koppelt de communicatiemodule aan OpenMRS?
## Context and Problem Statement
De communicatiemodule heeft afspraakdata nodig van OpenMRS om notificaties te kunnen versturen. De vraag is via welk mechanisme deze data bij de module terechtkomt. Dit is geen triviale keuze: de module moet meerdere OpenMRS-instanties bedienen, moet bestand zijn tegen downtime aan beide kanten, en moet aansluiten op de HL7/FHIR-standaard die OpenMRS hanteert.

Er zijn drie fundamenteel verschillende benaderingen denkbaar: de module vraagt zelf periodiek data op bij OpenMRS (polling), OpenMRS stuurt data naar de module wanneer er iets verandert (webhook/push), of OpenMRS publiceert events op een gedeelde berichtenbus waar de module op luistert (event streaming).

---
## Considered Options

### Optie A: Polling via de OpenMRS FHIR REST API
De communicatiemodule bevraagt periodiek de OpenMRS FHIR API om aankomende afspraken op te halen. De scheduler draait bijvoorbeeld elke minuut en vraagt: "geef mij alle afspraken die over 24 uur of 1 uur plaatsvinden."

Voordelen:
- Eenvoudig te implementeren: OpenMRS biedt via de FHIR2 module een `/Appointment` endpoint aan
- De module hoeft aan OpenMRS-kant verder niks te installeren of configureren buiten de FHIR2 module zelf
- Volledig in lijn met de FHIR-standaard, data komt al in het juiste formaat binnen
- Bij downtime van de module gaan er geen events verloren: bij herstel wordt gewoon opnieuw gepolled
- Schaalt goed naar meerdere OpenMRS-instanties: elke tenant krijgt een volledig onafhankelijke polling-configuratie, waardoor instanties elkaar niet beïnvloeden

Nadelen:
- Polling introduceert een kleine vertraging (tot 1 minuut) tussen afspraakaanmaak en detectie
- Bij veel instanties groeit het aantal uitgaande API-calls lineair
- OpenMRS moet bereikbaar zijn op het moment van polling: tijdelijke downtime betekent dat de module even blind is
- Vraagt om goede idempotentie-logica zodat afspraken niet dubbel worden verwerkt

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
- De feed is niet FHIR-native; de module moet zelf de mapping doen van OpenMRS-datamodel naar FHIR Appointment
- Complexere infrastructuur dan polling
- Minder geschikt voor het SaaS-model: de module moet per tenant een aparte feed-positie bijhouden en de feed-URL kennen

---
## Decision Outcome
**Gekozen: Optie A — Polling via de OpenMRS FHIR REST API**

**Justification**
De kernafweging gaat over betrouwbaarheid versus complexiteit. Webhooks lijken aantrekkelijk vanwege de real-time aard, maar het verlies van events bij downtime is in een medische context onaanvaardbaar zonder aanvullende retry-infrastructuur aan OpenMRS-kant die we niet kunnen garanderen. AtomFeed lost dit op, maar introduceert installatieverplichtingen bij elke klant en een niet-FHIR data-formaat.

Polling via de FHIR API biedt het beste evenwicht: geen extra installatie aan OpenMRS-kant buiten de FHIR2 module, native FHIR-compliance, en robuust gedrag bij downtime aan beide kanten. Daarnaast schaalt de aanpak horizontaal: elke tenant is volledig onafhankelijk geconfigureerd, waardoor het toevoegen van een nieuwe OpenMRS-instantie geen impact heeft op bestaande tenants. Het nadeel van polling-latency is in dit domein acceptabel: notificaties worden 24 uur en 1 uur voor de afspraak verstuurd, waarbij een vertraging van maximaal 1 minuut geen klinisch verschil maakt.

---
## Consequences
Good, because:
- De FHIR `/Appointment` resource geeft data terug in een gestandaardiseerd formaat, wat HL7-validatie aan onze kant eenvoudiger maakt
- Bij downtime van de module gaan geen events verloren; bij herstel wordt de volgende polling-cyclus gewoon uitgevoerd
- Nieuwe OpenMRS-instanties toevoegen is een kwestie van een endpoint en credentials registreren, zonder aanpassingen aan de OpenMRS-installatie zelf

Neutraal, because:
- De module moet idempotentie goed implementeren zodat een afspraak die al in de wachtrij staat niet opnieuw wordt ingepland bij de volgende poll
- Het polling-interval moet zorgvuldig gekozen worden: te kort verhoogt de load op OpenMRS, te lang vergroot de kans op late detectie bij afspraken die kort van tevoren worden aangemaakt

Bad, because:
- Bij downtime van OpenMRS ziet de module tijdelijk geen nieuwe of gewijzigde afspraken; afspraken die tijdens die downtime worden aangemaakt kunnen hun 24-uurs notificatie missen als de downtime lang genoeg duurt
- Het aantal uitgaande API-calls groeit met het aantal tenants; bij honderden instanties is rate limiting en request-spreiding noodzakelijk

---
## More information
Implementatieaandachtspunten:
- De FHIR2 module van OpenMRS moet geïnstalleerd zijn en Appointment-ondersteuning bevatten; dit is beschikbaar vanaf OpenMRS 2.7.x maar vereist expliciete verificatie bij onboarding van een nieuwe tenant
- De module gebruikt de FHIR query `GET /fhir/Appointment?date=gt[now]&date=lt[now+25h]` om aankomende afspraken op te halen
- Per tenant wordt de laatste succesvolle poll-timestamp opgeslagen zodat gemiste polls gedetecteerd kunnen worden
- FHIR-responses worden gevalideerd op structuur en verplichte velden voordat ze de queue ingaan
- Authenticatie richting OpenMRS verloopt via een per-tenant API-key die versleuteld wordt opgeslagen
- De module ondersteunt meerdere OpenMRS-versies zolang de FHIR R4 API beschikbaar is, wat het geval is vanaf OpenMRS 2.7.x