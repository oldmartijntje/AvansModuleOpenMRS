---
tags:
  - ADR
status: Accepted
deciders:
  - chris
  - martijn
  - angel
---
## Context and Problem Statement
Afspraken kunnen worden geannuleerd vanuit twee richtingen. Een arts of planningsmedewerker annuleert een afspraak via de OpenMRS interface. Een patiënt reageert op een ontvangen notificatie met een annuleringsverzoek. In beide gevallen moet de communicatiemodule de annulering correct verwerken, verdere notificaties voor de afspraak stoppen, en de patiënt een bevestiging sturen.

De vraag is hoe beide annuleringsstromen worden ontworpen zodat OpenMRS te allen tijde de bron van waarheid blijft, de database van de communicatiemodule consistent blijft met de staat in OpenMRS, en er geen notificaties worden verstuurd voor geannuleerde afspraken.

---
## Considered Options
### Optie A: Directe databasemutatie zonder bevestiging vanuit OpenMRS
Bij ontvangst van een annuleringsverzoek markeert de communicatiemodule de afspraak direct als CANCELLED in zijn eigen database zonder te wachten op bevestiging vanuit OpenMRS.

Voordelen:
- Eenvoudiger te implementeren
- Snellere verwerking

Nadelen:
- De communicatiemodule en OpenMRS kunnen uit sync raken als de OpenMRS-aanroep later mislukt
- OpenMRS is niet langer de bron van waarheid
- Bij een patiëntannulering die OpenMRS niet bereikt staat de afspraak nog steeds als actief in OpenMRS terwijl de communicatiemodule hem als geannuleerd beschouwt

### Optie B: Event-driven annulering met OpenMRS als bron van waarheid
De communicatiemodule markeert een afspraak als PENDING_CANCELLATION bij ontvangst van een annuleringsverzoek. De daadwerkelijke CANCELLED status wordt pas gezet nadat de OpenMRS plugin een VOIDED event heeft gepubliceerd naar de externe RabbitMQ queue als bevestiging dat OpenMRS de annulering heeft verwerkt. Pas na ontvangst van dit bevestigingsevent stuurt de communicatiemodule een bevestigingsnotificatie naar de patiënt.

Voordelen:
- OpenMRS blijft te allen tijde de bron van waarheid
- De database van de communicatiemodule is altijd consistent met de staat in OpenMRS
- Afspraken met status PENDING_CANCELLATION worden door de scheduler al overgeslagen zodat geen nieuwe notificatiejobs worden aangemaakt tijdens de verwerking
- De stroom is identiek voor beide annuleringsrichtingen vanaf het moment dat het VOIDED event binnenkomt

Nadelen:
- Meer complexiteit: de annuleringsstroom loopt via meerdere stappen en twee queue-lagen
- Als het VOIDED event nooit binnenkomt blijft de afspraak in PENDING_CANCELLATION status; dit vereist een timeout-mechanisme

---
## Decision Outcome
**Gekozen: Optie B — Event-driven annulering met OpenMRS als bron van waarheid**

**Justification**
In een medische context is consistentie tussen de communicatiemodule en het bronsysteem OpenMRS niet onderhandelbaar. Een situatie waarbij OpenMRS een afspraak als actief beschouwt terwijl de communicatiemodule hem als geannuleerd behandelt kan leiden tot het niet versturen van notificaties voor een afspraak die nog steeds plaatsvindt. Optie A accepteert dit risico expliciet en is daarom onaanvaardbaar.

De PENDING_CANCELLATION status zorgt ervoor dat de scheduler geen nieuwe notificatiejobs aanmaakt voor de afspraak terwijl de annulering wordt verwerkt, zonder dat de definitieve CANCELLED status al is gezet. Dit voorkomt race conditions waarbij de scheduler een notificatiejob aanmaakt in de tijd tussen het ontvangen van het annuleringsverzoek en het binnenkomen van het bevestigingsevent.

---
## Annuleringsstroom: dokterszijde
De arts annuleert een afspraak via de OpenMRS interface. De OpenMRS plugin detecteert het VOIDED event via de Bahmni Appointment Scheduling module en publiceert een bericht met eventType CANCELLED naar de externe RabbitMQ exchange. Container 1 ontvangt dit event via `appointment.inbound`, zoekt de afspraak op in de database via het appointmentUuid, en zet de status op CANCELLED. De scheduler slaat deze afspraak voortaan over bij het aanmaken van notificatiejobs. Als er al notificatiejobs op `notification.queue` staan worden deze door container 3 geskipt op basis van de CANCELLED status in de database. Container 3 stuurt een bevestigingsnotificatie naar de patiënt dat de afspraak is geannuleerd.

## Annuleringsstroom: patiëntzijde
De patiënt ontvangt een notificatie met een interactieve annuleringsknop. In productie stuurt de messaging provider een webhook naar het `/api/cancellation` endpoint op container 1 wanneer de patiënt op de knop tikt. Voor demonstratiedoeleinden wordt dit gesimuleerd met een curl-aanroep naar hetzelfde endpoint.

Container 1 ontvangt het annuleringsverzoek, valideert dat de afspraak bestaat en nog niet geannuleerd is, en zet de status op PENDING_CANCELLATION in de database. Container 1 plaatst het verzoek op `cancellation.queue`. Container 1 consumeert van `cancellation.queue` en roept de OpenMRS REST API aan op de juiste tenant om de afspraak te annuleren via `POST /openmrs/ws/rest/v1/appointments/{uuid}/status` met status CANCELLED. Vanaf dit punt volgt de stroom de dokterszijde: de OpenMRS plugin publiceert een VOIDED event, container 1 ontvangt dit, zet de status op CANCELLED, en container 3 stuurt de bevestigingsnotificatie.

### Timeout-mechanisme
Als het verwachte VOIDED bevestigingsevent niet binnenkomt binnen 5 minuten na het instellen van PENDING_CANCELLATION markeert de scheduler de afspraak als CANCELLATION_FAILED en logt een waarschuwing in het observability dashboard. De hospital IT-beheerder wordt via het dashboard geattendeerd op handmatige controle.

---
## Consequences
Good, because:
- OpenMRS blijft te allen tijde de bron van waarheid voor afspraakdata
- De database van de communicatiemodule is altijd consistent met OpenMRS
- Beide annuleringsstromen convergeren op hetzelfde punt: het VOIDED event in de externe queue
- PENDING_CANCELLATION voorkomt race conditions bij het aanmaken van notificatiejobs

Neutraal, because:
- De patiëntzijde annuleringsstroom vereist dat container 1 zowel produceert op als consumeert van `cancellation.queue`; dit is een bewuste keuze om de annuleringslogica centraal in container 1 te houden
- Voor demonstratiedoeleinden wordt de provider webhook gesimuleerd met curl; de architectuur is identiek aan de productiesituatie

Bad, because:
- Als het VOIDED bevestigingsevent niet binnenkomt blijft de afspraak in PENDING_CANCELLATION; het timeout-mechanisme mitigeert dit maar vereist handmatige interventie
- De annuleringsstroom is afhankelijk van de beschikbaarheid van zowel RabbitMQ als de OpenMRS REST API; als een van beide tijdelijk niet beschikbaar is kan de stroom vastlopen

---
## More information
Implementatieaandachtspunten:
- De afspraakstatus in de database kent de volgende waarden: SCHEDULED, PENDING_CANCELLATION, CANCELLED, CANCELLATION_FAILED
- Container 1 exposeert het endpoint `POST /api/cancellation` voor inkomende provider webhooks
- De OpenMRS REST call voor annulering verloopt via `POST /openmrs/ws/rest/v1/appointments/{uuid}/status` met Basic Auth credentials per tenant
- De timeout-check voor PENDING_CANCELLATION wordt uitgevoerd door container 2 als onderdeel van de reguliere scheduler-cyclus
- Zie [[ADR4_Queues-05-06|ADR 4]] voor het queue-ontwerp inclusief `cancellation.queue`
- Zie [[ADR6_Drie_Container_Architectuur-05-12|ADR 6]] voor de verantwoordelijkheidsverdeling tussen de drie containers