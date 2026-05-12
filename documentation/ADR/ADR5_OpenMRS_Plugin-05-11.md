---
tags:
  - ADR
status: Accepted
deciders: 
- chris
- martijn
---
## Context and Problem Statement
De communicatiemodule heeft een mechanisme nodig waarmee individuele OpenMRS-instanties afspraakgebeurtenissen kunnen doorzenden naar de centrale externe RabbitMQ exchange op de VPS. Dit is de ingang van de gehele pipeline: zonder correcte events vanuit OpenMRS ontvangt de inbound processor (container 1) geen data en worden er geen notificaties verstuurd.

De vraag is hoe deze integratie technisch wordt gerealiseerd aan de OpenMRS-kant. De oplossing moet passen binnen de OpenMRS module-architectuur, werken met de Bahmni Appointment Scheduling module die in de referentie-installatie aanwezig is, en de communicatiemodule volledig ontkoppelen van de beschikbaarheid van individuele OpenMRS-instanties.

---
## Considered Options
### Optie A: Bestaande OpenMRS Event Module uitbreiden
De OpenMRS Event Module vuurt al events via Apache ActiveMQ wanneer domeinobjecten worden aangemaakt, gewijzigd of verwijderd. Een bestaande listener zou uitgebreid kunnen worden om events ook naar de externe RabbitMQ te forwarden.

Voordelen:
- Maakt gebruik van bestaande infrastructuur
- Events worden al gefired door de OpenMRS kern

Nadelen:
- De OpenMRS Event Module gebruikt Apache ActiveMQ als interne broker, niet RabbitMQ; een brug tussen de twee is complex en foutgevoelig
- De bestaande events zijn generieke OpenMRS domein-events, niet Bahmni appointment-specifiek
- Aanpassen van een bestaande module introduceert het risico van onbedoelde bijwerkingen op andere functionaliteit

### Optie B: Zelfgebouwde OpenMRS OMOD plugin
Een nieuwe plugin wordt gebouwd als een standaard OpenMRS module. Deze plugin registreert zichzelf als listener op Bahmni appointment events en publiceert deze rechtstreeks naar de externe RabbitMQ exchange van de communicatiemodule.

Voordelen:
- Volledige controle over welke events worden gepubliceerd en in welk formaat
- Geen risico van bijwerkingen op bestaande modules
- Enkelvoudige verantwoordelijkheid: luisteren op Bahmni appointment events en doorsturen naar RabbitMQ
- Het OMOD formaat is de standaard distributiemethode voor OpenMRS modules
- Configuratie via OpenMRS global properties past binnen de bestaande beheersstructuur

Nadelen:
- Vereist kennis van de OpenMRS module development API
- Voegt een extra codebase toe naast de drie Spring Boot applicaties
- Moet bij elke tenant apart geïnstalleerd en geconfigureerd worden

### Optie C: Polling vanuit container 1
Container 1 pollt periodiek de Bahmni Appointments REST API per tenant in plaats van events te ontvangen.

Voordelen:
- Geen installatie aan OpenMRS-kant nodig buiten de Bahmni module

Nadelen:
- Eerder onderzocht en afgevallen als primaire integratiemethode vanwege annuleringsproblemen en temporele koppeling
- Zie ADR 3 voor de volledige afweging
---
## Decision Outcome
**Gekozen: Optie B, Zelfgebouwde OpenMRS OMOD plugin**

**Justification**
De plugin-aanpak geeft volledige controle over het event formaat en de routing naar RabbitMQ zonder afhankelijk te zijn van de interne ActiveMQ infrastructuur van de OpenMRS Event Module. De plugin heeft bewust een minimale scope: hij doet niets anders dan luisteren op Bahmni appointment events en deze doorsturen naar de externe RabbitMQ exchange. Dit houdt de codebase klein, de verantwoordelijkheid enkelvoudig, en het risico op fouten laag.

De configuratie via OpenMRS global properties past binnen de vertrouwde beheersstructuur van OpenMRS en vereist geen technische kennis van RabbitMQ van de hospital IT-beheerder. Container 1 is de enige consumer van de externe queue en hoeft niets te weten over de plugin implementatie, wat de twee codebases volledig ontkoppeld houdt.

---
## Plugin architectuur
De plugin bestaat uit drie onderdelen.

De activator is de entry point van de OMOD module. Hij wordt aangeroepen door OpenMRS bij het opstarten, valideert de global properties, en registreert de event listeners. Als een verplichte global property ontbreekt logt hij een foutmelding en registreert geen listeners zodat het systeem geen berichten verstuurt naar een onbekende bestemming. Bij afsluiten ruimt hij de listeners en de RabbitMQ verbinding netjes op.

De event listener luistert op drie event types die door de Bahmni Appointment Scheduling module worden gefired via de OpenMRS Event Module: CREATED wanneer een nieuwe afspraak wordt aangemaakt, UPDATED wanneer een bestaande afspraak wordt gewijzigd, en VOIDED wanneer een afspraak wordt geannuleerd. Voor elk ontvangen event haalt de listener de volledige afspraakdata op inclusief het telefoonnummer van de patiënt via `GET /openmrs/ws/rest/v1/patient/{uuid}`, bouwt een JSON bericht op, en geeft dit door aan de publisher.

De publisher beheert de verbinding met de externe RabbitMQ op de VPS en publiceert berichten naar `appointment.exchange` met de routing key `appointment.{tenantId}.{eventType}`. De publisher implementeert retry-logica: als RabbitMQ tijdelijk niet bereikbaar is worden berichten tot drie keer opnieuw geprobeerd met exponential backoff tussen pogingen.

---
## Berichtformaat
Elk bericht dat de plugin publiceert bevat de volgende velden:
```json
{
  "tenantId": "hospital-amsterdam",
  "eventType": "CREATED",
  "timestamp": "2025-01-22T13:00:00Z",
  "appointmentUuid": "395a61e4-83a0-4ed0-9697-568e7196be02",
  "patientUuid": "f97cbd02-73cd-4318-a8e0-e6e8d958d17d",
  "patientName": "Mark Williams",
  "phoneNumber": "06-12345678",
  "startDateTime": "2025-01-23T14:00:00Z",
  "endDateTime": "2025-01-23T14:30:00Z",
  "serviceName": "General Medicine service",
  "status": "Scheduled"
}
```

---
## Configuratie per tenant
De plugin leest twee verplichte global properties uit OpenMRS bij het opstarten.

`communicatie.tenantId` is een unieke identifier voor deze OpenMRS-instantie, ingesteld door de hospital IT-beheerder bij onboarding. Dit is de identifier die in de routing key en in elk bericht wordt opgenomen.

`communicatie.rabbitmq.url` is de verbindingsURL naar de centrale RabbitMQ instantie op de VPS inclusief gebruikersnaam en wachtwoord. De credentials zijn uniek per tenant en worden verstrekt door de beheerder van de communicatiemodule bij onboarding.

---
## Consequences
Good, because:
- De plugin heeft een enkelvoudige verantwoordelijkheid en een minimale codebase
- Container 1 ontvangt events in real-time zonder polling-lag
- Annuleringen worden direct als VOIDED event ontvangen zodat container 1 de database direct kan updaten
- Het OMOD formaat is vertrouwd bij OpenMRS beheerders en past in de standaard installatieprocedure
- Foutieve configuratie wordt bij het opstarten gedetecteerd en gelogd zodat het systeem niet stil faalt

Neutraal, because:
- De plugin moet bij elke nieuwe tenant worden geïnstalleerd en geconfigureerd; dit is gedocumenteerd in de onboarding procedure voor technisch beheerders
- Het berichtformaat moet stabiel blijven zolang er tenants actief zijn; wijzigingen vereisen een gecoördineerde update van zowel de plugin als container 1

Bad, because:
- Als de externe RabbitMQ tijdelijk niet bereikbaar is kunnen events verloren gaan als de retry-pogingen uitgeput zijn; dit wordt gemonitord via de observability stack
- De plugin introduceert een extra codebase naast de drie Spring Boot applicaties die onderhouden moet worden
- Foutieve configuratie van de global properties leidt tot een plugin die geen berichten verstuurt zonder dat dit direct zichtbaar is in de OpenMRS interface; monitoring op de externe RabbitMQ exchange is noodzakelijk om dit te detecteren

---
## More information
Implementatieaandachtspunten:
- De plugin wordt gebouwd als een Maven project met de OpenMRS module archetype als basis
- Dependencies: OpenMRS core API, Bahmni Appointments module API, RabbitMQ AMQP client library
- Het OMOD bestand wordt geplaatst in de OpenMRS modules directory en wordt automatisch geladen bij herstart van OpenMRS
- De plugin verbindt met de externe RabbitMQ via TLS 1.3 op poort 5671
- Zie ADR 4 voor het volledige queue en exchange ontwerp
- Zie ADR 3 voor de motivatie achter de keuze voor event-driven integratie boven polling
- Zie ADR 6 voor de drie-container architectuur die de plugin als ingang gebruikt
- Zie ADR 8 voor de beveiligingseisen rondom de RabbitMQ verbinding vanuit de plugin