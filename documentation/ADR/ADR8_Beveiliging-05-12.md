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
De communicatiemodule verwerkt medische patiëntdata inclusief namen, telefoonnummers en afspraakdetails. Deze data beweegt tussen OpenMRS-instanties, de RabbitMQ broker, drie Spring Boot containers, en externe messaging providers. De module is als SaaS opgezet waarbij meerdere tenants data delen op dezelfde infrastructuur.

De niet-functionele eisen stellen dat gevoelige informatie versleuteld moet worden opgeslagen met minimaal AES-256 en getransporteerd via TLS 1.3, dat authenticatiegegevens nooit in code of configuratiebestanden mogen staan, en dat logbestanden geen direct identificeerbare patiëntgegevens mogen bevatten. Daarnaast moet de multi-tenant infrastructuur zodanig zijn ingericht dat een tenant nooit data van een andere tenant kan lezen of beïnvloeden.

---
## Beveiligingsdomeinen
De beveiliging wordt uitgewerkt over vier domeinen: transport, opslag, authenticatie en autorisatie, en logging.

---
## Transport
Alle communicatie tussen componenten verloopt via TLS 1.3. Dit geldt voor de verbinding van de OpenMRS plugin naar de externe RabbitMQ (poort 5671), de verbinding van de drie Spring Boot containers naar RabbitMQ, de verbinding van container 1 naar de OpenMRS REST API, de verbinding van container 3 naar de externe messaging providers, en de verbinding van provider webhooks naar container 1.

De standaard onversleutelde RabbitMQ poort 5672 is gesloten op de VPS firewall. Alleen poort 5671 (TLS) is extern bereikbaar. De RabbitMQ management UI op poort 15672 is alleen bereikbaar via een intern Docker netwerk en niet extern exposed.

---
## Opslag
Patiëntdata en afspraakdetails worden versleuteld opgeslagen in MariaDB met AES-256 op kolomniveau voor de velden patientName, phoneNumber en appointmentDetails. De versleuteling wordt uitgevoerd door container 1 voordat data naar de database wordt geschreven. Containers 2 en 3 ontvangen de data in ontsleutelde vorm na ophalen uit de database.

Berichten op de RabbitMQ queues worden versleuteld met AES-256 door de producer voordat het bericht wordt gepubliceerd. De consumer decodeert het bericht na ontvangst. Dit zorgt ervoor dat zelfs bij ongeautoriseerde toegang tot de RabbitMQ management interface de berichtinhoud niet leesbaar is.

Patiënt- en afspraakdata wordt automatisch verwijderd uit de database 14 dagen na afhandeling van de laatste notificatie voor die afspraak. Metainformatie zonder directe patiëntidentificatoren wordt maximaal één jaar bewaard voor factuurcontrole.

---
## Authenticatie en autorisatie
Alle credentials worden opgeslagen als environment variabelen in Docker Compose en nooit in code of configuratiebestanden. Dit geldt voor de MariaDB gebruikersnaam en wachtwoord, de RabbitMQ gebruikersnaam en wachtwoord per tenant, en de API-sleutels van de externe messaging providers per tenant.

Elke OpenMRS plugin maakt verbinding met RabbitMQ via een eigen gebruikersnaam en wachtwoord die uniek zijn per tenant. Deze per-tenant credentials worden verstrekt door de beheerder van de communicatiemodule bij onboarding en worden door de hospital IT-beheerder ingesteld als OpenMRS global property. Een tenant-gebruiker in RabbitMQ heeft alleen schrijfrechten op de `appointment.exchange` met zijn eigen tenantId als routing key prefix. Dit voorkomt dat tenant A berichten kan publiceren met de tenantId van tenant B.

De drie Spring Boot containers gebruiken een apart serviceaccount in RabbitMQ met leesrechten op `appointment.inbound` en `cancellation.queue` voor container 1, schrijfrechten op `notification.queue` voor container 2, en leesrechten op `notification.queue` voor container 3.

Provider API-sleutels worden per tenant opgeslagen in de database versleuteld met AES-256 en worden door container 3 opgehaald op het moment van notificatieverzending. Ze worden nooit als onderdeel van een notificatiebericht op een queue geplaatst.

---
## Logging
Logbestanden bevatten nooit directe patiëntidentificatoren. De volgende velden worden nooit gelogd: patientName, phoneNumber, appointmentDetails. Logs bevatten uitsluitend: appointmentUuid, tenantId, eventType, notificationType, verwerkingsstatus en tijdstempel.

Alle containers exporteren logs via OpenTelemetry naar de centrale logging stack. Logs worden minimaal één jaar bewaard voor auditdoeleinden. Toegang tot de logging stack is beperkt tot geautoriseerde beheerders.

---
## Consequences
Good, because:
- Patiëntdata is versleuteld zowel in transit als at rest waardoor onderschepping of ongeautoriseerde toegang de data niet bruikbaar maakt
- Per-tenant RabbitMQ credentials voorkomen cross-tenant datalekkage op queue-niveau
- Credentials staan nooit in code of configuratiebestanden waardoor een gelekte codebase geen toegang geeft tot productiedata
- Automatische datavervaldatum van 14 dagen beperkt de exposure bij een eventueel datalek

Neutraal, because:
- AES-256 versleuteling op kolomniveau in MariaDB vereist sleutelbeheer; de encryptiesleutel wordt als environment variabele meegegeven aan container 1 en mag nooit in de database zelf worden opgeslagen
- Per-tenant credential beheer voegt operationele complexiteit toe aan het onboardingproces van nieuwe tenants; dit is gedocumenteerd in de technische beheerdershandleiding

Bad, because:
- Kolomniveau versleuteling in MariaDB maakt het uitvoeren van queries op versleutelde velden onmogelijk zonder ontsleuteling; dit wordt gemitigeerd door alleen niet-versleutelde metavelden te gebruiken als zoeksleutel
- TLS configuratie en certificaatbeheer op de VPS vereist periodieke certificaatvernieuwing; verlopen certificaten blokkeren alle verbindingen van OpenMRS plugins naar RabbitMQ

---
## More information
Implementatieaandachtspunten:
- TLS certificaten voor RabbitMQ worden gegenereerd via Let's Encrypt en automatisch vernieuwd via certbot
- De AES-256 encryptiesleutel voor kolomversleuteling wordt meegegeven als environment variabele `DB_ENCRYPTION_KEY` aan container 1
- De AES-256 encryptiesleutel voor berichtversleuteling op RabbitMQ wordt gedeeld tussen de OpenMRS plugin en container 1 via de global property `communicatie.encryption.key` en de environment variabele `AMQP_ENCRYPTION_KEY`
- Spring Boot applicaties gebruiken geen hardcoded wachtwoorden; alle secrets worden geladen via `@Value` annotaties gekoppeld aan environment variabelen
- Docker Compose bestanden met environment variabelen worden nooit gecommit naar de git repository; een `.env.example` bestand met lege waarden wordt als template meegeleverd
- Zie [[ADR4_Queues-05-06|ADR 4]]voor de RabbitMQ infrastructuur waarop deze beveiligingsmaatregelen van toepassing zijn
- Zie [[ADR6_Drie_Container_Architectuur-05-12|ADR 6]] voor de container architectuur waarop de authenticatie en autorisatie per container van toepassing zijn