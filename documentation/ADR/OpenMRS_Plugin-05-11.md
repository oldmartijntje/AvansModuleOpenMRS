---
tags:
  - ADR
status: Accepted
deciders: 
- chris
- martijn
---
## Context and Problem Statement
De communicatiemodule heeft een mechanisme nodig waarmee individuele OpenMRS-instanties afspraakgebeurtenissen kunnen doorzenden naar de centrale RabbitMQ infrastructuur. Dit is de kern van de integratie tussen het bestaande OpenMRS-systeem en de nieuwe communicatiemodule.

De vraag is hoe deze integratie technisch wordt gerealiseerd aan de OpenMRS-kant. De oplossing moet passen binnen de OpenMRS module-architectuur, werken met de Bahmni Appointment Scheduling module die in de referentie-installatie aanwezig is, en de communicatiemodule volledig ontkoppelen van de beschikbaarheid van individuele OpenMRS-instanties.

---
## Considered Options
**Optie A: Bestaande OpenMRS Event Module uitbreiden**

De OpenMRS Event Module vuurt al events via Apache ActiveMQ wanneer domeinobjecten worden aangemaakt, gewijzigd of verwijderd. Een bestaande listener in deze module zou uitgebreid kunnen worden om events ook naar een externe RabbitMQ te forwarden.

Voordelen:
- Maakt gebruik van bestaande infrastructuur
- Events worden al gefired door de OpenMRS kern

Nadelen:
- De OpenMRS Event Module gebruikt Apache ActiveMQ als interne broker, niet RabbitMQ; een brug tussen de twee is complex en foutgevoelig
- De bestaande events zijn generieke OpenMRS domein-events, niet Bahmni appointment-specifiek
- Aanpassen van een bestaande module introduceert het risico van onbedoelde bijwerkingen op andere functionaliteit die van dezelfde module afhankelijk is

## Optie B: Zelfgebouwde OpenMRS OMOD plugin
Een nieuwe plugin wordt gebouwd als een standaard OpenMRS module (OMOD bestand). Deze plugin registreert zichzelf als listener op Bahmni appointment events via de OpenMRS Event Module API en publiceert deze events rechtstreeks naar de RabbitMQ exchange van de communicatiemodule.

Voordelen:
- Volledige controle over welke events worden gepubliceerd en in welk formaat
- Geen risico van bijwerkingen op bestaande modules
- De plugin heeft één enkelvoudige verantwoordelijkheid: luisteren en doorsturen
- Het OMOD formaat is de standaard distributiemethode voor OpenMRS modules en is vertrouwd bij OpenMRS beheerders
- Configuratie via OpenMRS global properties past binnen de bestaande beheersstructuur van OpenMRS

Nadelen:
- Vereist kennis van de OpenMRS module development API
- Voegt een tweede codebase toe aan het project
- Moet bij elke tenant apart geïnstalleerd en geconfigureerd worden

## Optie C: Polling vanuit de communicatiemodule
In plaats van een plugin aan OpenMRS-kant pollt de communicatiemodule periodiek de Bahmni Appointments REST API per tenant.

Voordelen:
- Geen installatie aan OpenMRS-kant nodig
- Eenvoudiger te ontwikkelen

Nadelen:
- Polling-latency tot 1 minuut
- Annuleringen zijn lastig betrouwbaar te detecteren zonder extra status-check calls
- De communicatiemodule blijft temporeel gekoppeld aan de beschikbaarheid van OpenMRS op het moment van polling
- Eerder onderzocht en afgevallen als primaire integratiemethode, zie ADR 3

---
## Decision Outcome
**Gekozen: Optie B — Zelfgebouwde OpenMRS OMOD plugin**

**Justification**
De plugin-aanpak geeft volledige controle over het event formaat en de routing naar RabbitMQ zonder afhankelijk te zijn van de interne ActiveMQ infrastructuur van de OpenMRS Event Module. Polling is eerder in ADR 3 afgevallen als primaire integratiemethode vanwege de problemen met annuleringsdetectie en temporele koppeling.

De plugin heeft bewust een minimale scope: hij doet niets anders dan luisteren op Bahmni appointment events en deze doorsturen naar RabbitMQ. Dit houdt de codebase klein, de verantwoordelijkheid enkelvoudig, en het risico op fouten laag. De configuratie via OpenMRS global properties past binnen de vertrouwde beheersstructuur van OpenMRS en vereist geen technische kennis van RabbitMQ van de hospital IT-beheerder.

---
## Plugin architectuur
De plugin bestaat uit drie onderdelen.

De activator is de entry point van de OMOD module. Hij wordt aangeroepen door OpenMRS bij het opstarten en registreert de event listeners. Bij afsluiten van OpenMRS ruimt hij de listeners en de RabbitMQ verbinding netjes op.

De event listener luistert op drie event types die door de Bahmni Appointment Scheduling module worden gefired: CREATED wanneer een nieuwe afspraak wordt aangemaakt, UPDATED wanneer een bestaande afspraak wordt gewijzigd, en VOIDED wanneer een afspraak wordt geannuleerd of verwijderd. Voor elk ontvangen event bouwt de listener een JSON bericht op en geeft dit door aan de publisher.

De publisher beheert de verbinding met RabbitMQ en publiceert berichten naar de `appointment.exchange` topic exchange met de routing key `appointment.{tenantId}.{eventType}`. De publisher implementeert retry-logica: als RabbitMQ tijdelijk niet bereikbaar is worden berichten tot drie keer opnieuw geprobeerd met een korte wachttijd tussen pogingen.

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

Het telefoonnummer wordt opgehaald via een aanvullende call naar `GET /openmrs/ws/rest/v1/patient/{uuid}` op het moment dat het event wordt verwerkt door de plugin, zodat de communicatiemodule dit veld niet meer zelf hoeft op te halen.

---
## Configuratie per tenant
De plugin leest twee verplichte global properties uit OpenMRS bij het opstarten:

`communicatie.tenantId` is een unieke identifier voor deze OpenMRS-instantie, ingesteld door de hospital IT-beheerder bij onboarding. Dit is de identifier die in de routing key en in elk bericht wordt opgenomen.

`communicatie.rabbitmq.url` is de verbindingsURL naar de centrale RabbitMQ instantie op de VPS, inclusief gebruikersnaam en wachtwoord. De credentials zijn uniek per tenant en worden door de beheerder van de communicatiemodule verstrekt bij onboarding.

Als een van deze properties niet is ingesteld logt de plugin een foutmelding bij het opstarten en registreert geen event listeners, zodat het systeem geen berichten verstuurt naar een verkeerde of onbekende bestemming.

---
## Consequences
Good, because:
- De plugin heeft een enkelvoudige verantwoordelijkheid en een minimale codebase
- De communicatiemodule ontvangt events in real-time zonder polling-lag
- Annuleringen worden direct als VOIDED event ontvangen en correct verwerkt
- Het OMOD formaat is vertrouwd bij OpenMRS beheerders en past in de standaard installatieprocedure
- Configuratie via global properties vereist geen technische kennis van RabbitMQ van de hospital IT-beheerder

Neutraal, because:
- De plugin moet bij elke nieuwe tenant worden geïnstalleerd en geconfigureerd; dit is gedocumenteerd als onderdeel van de onboarding procedure
- Het berichtformaat moet stabiel blijven zolang er tenants actief zijn; wijzigingen aan het formaat vereisen een gecoördineerde update van zowel de plugin als de communicatiemodule

Bad, because:
- Als RabbitMQ tijdelijk niet bereikbaar is kunnen events verloren gaan als de retry-pogingen uitgeput zijn; dit wordt gemonitord via de observability stack
- De plugin introduceert een tweede codebase die onderhouden moet worden naast de Spring Boot applicatie
- Foutieve configuratie van de global properties leidt tot een plugin die geen berichten verstuurt zonder dat dit direct zichtbaar is in de OpenMRS interface; monitoring op de RabbitMQ exchange is noodzakelijk om dit te detecteren

---
## More information
Implementatieaandachtspunten:
- De plugin wordt gebouwd als een Maven project met de OpenMRS module archetype als basis
- De plugin heeft als dependencies: de OpenMRS core API, de Bahmni Appointments module API, en de RabbitMQ AMQP client library
- Het OMOD bestand wordt geplaatst in de OpenMRS modules directory en wordt automatisch geladen bij herstart van OpenMRS
- De plugin verbindt met RabbitMQ via TLS 1.3 op poort 5671; zie ADR 7 voor de beveiligingseisen
- Zie ADR 4 voor het volledige queue- en exchange-ontwerp
- Zie ADR 3 voor de motivatie achter de keuze voor event-driven integratie boven polling