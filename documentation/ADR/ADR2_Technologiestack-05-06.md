---
status: Creating
date: 2026-05-06
deciders: 
- angel
- chris
- martijn
tags:
  - ADR
---

# AD: technologiestack voor de communicatiemodule

## Context and Problem Statement
De communicatiemodule moet als zelfstandige SaaS-oplossing notificaties kunnen versturen voor meerdere OpenMRS-organisaties. De oplossing moet betrouwbaar, uitbreidbaar, veilig en goed te monitoren zijn. Daarnaast moet de module ook :

- Integreren met OpenMRS.
- Berichten kunnen verwerken via meerdere messaging-providers.
- Retry en fallbackmechanismen ondersteunen bij storingen.
- Kunnen draaien in een Docker omgeving.
- Aansluiten bij de capaciteiten van het team en haalbaar zijn binnen de scope van het project.

Voor deze ADR moeten keuzes worden gemaakt voor:
1. Programmeertaal en framework
2. Berichtenwachtrij
3. Opslag
4. Monitoringtooling

Voordat we een technologiestack kiezen, moeten we eerst in kaart brengen welke eisen uit de opdracht technisch het belangrijkst zijn. Deze eisen vertalen we vervolgens naar concrete technische behoeften. Op basis daarvan kunnen we onderbouwen welke programmeertaal, framework, berichtenwachtrij, opslagoplossing en welke monitoringtooling het beste passen.

--- 

### Kerneisen

- **K1:** Communicatiemodule moet als zelfstandige SaaS-oplossing kunnen draaien.  
    Belangrijk omdat het goed moet kunnen werken als losse service, los van OpenMRS.  
    Nodig zodat communicatie zelfstandig kan draaien.
- **K2:** Module moet notificaties 24 uur en 1 uur voor een afspraak kunnen versturen.  
    Belangrijk omdat dit een background proces met scheduling vereist.  
    Nodig voor notificaties op vaste tijdstippen.
- **K3:** Module moet met meerdere OpenMRS-instanties en meerdere messaging providers kunnen werken.  
    Belangrijk omdat dit een uitbreidbare en goed structureerbare backend vereist.  
    Nodig voor meerdere instanties en providers.
- **K4:** Downtime van providers moet worden opgevangen met retry en fallback.  
    Belangrijk omdat dit asynchrone verwerkingen en queue/broker vereist.  
    Nodig voor retry, buffering en foutafhandelingen.
- **K5:** Module moet veilig omgaan met gevoelige gegevens.  
    Belangrijk vanwege betrouwbare frameworks, opslag en configuratiebeheer.  
    Nodig voor credentials, tokens en gevoelige data.
- **K6:** Werking van module moet inzichtbaar zijn via monitoring/real-time dashboard.  
    Belangrijk omdat hiervoor een monitoringstool nodig is.  
    Nodig voor inzicht in status, fouten en prestaties.
- **K7:** Module moet in een Docker-omgeving kunnen draaien.  
    Belangrijk voor ontwikkelomgeving en haalbaarheid.  
    Nodig voor deployment en lokale setups.
- **K8:** Module moet haalbaar zijn voor ons team binnen de projectscope.  
    Belangrijk door de combinatie van bestaande teamkennis en eenvoud.  
    Nodig omdat het haalbaar moet zijn voor het team.

### Decision drivers

1. **De module moet als zelfstandige service kunnen draaien.**  
    Daarom is er een framework nodig dat geschikt is voor een stand-alone backend-service. Spring Boot documenteert ondersteuning voor production-ready features, task execution/scheduling, messaging en container/Docker Compose-ondersteuning. ASP.NET Core documenteert dependency injection en hosted services voor background tasks.
2. **Notificaties moeten 24 uur en 1 uur vooraf verstuurd kunnen worden**.  
    Daarom is ondersteuning voor scheduling of background processen belangrijk. Spring Framework documenteert scheduling, en ASP.NET Core documenteert hosted services voor background tasks.
3. **Providerstoringen moeten worden opgevangen met retry/fallback**.  
    Daarom is een betrouwbare queue of broker vrij logisch. RabbitMQ documenteert queues, negative acknowledgements en dead-letter exchanges. 
    Kafka documenteert zichzelf als een event-streaming platform, wat krachtig is maar voor deze opdracht mogelijk zwaarder dan nodig.
4. **De module moet veilig en betrouwbaar configuratie, status en metadata opslaan**.  
    Daarom ligt een relationele database handig. PostgreSQL documenteert constraints en JSON-types; MariaDB documenteert transacties; MongoDB documenteert een flexibel documentmodel.
5. **De werking moet inzichtelijk zijn via monitoring en een dashboard**.  
    Daarom is monitoringstooling nodig. Prometheus documenteert time-series metrics, Grafana dashboards, en OpenTelemetry documenteert ondersteuning voor traces, metrics en logs.
6. **De stack moet haalbaar zijn voor een studententeam.**  
    Daarom wegen bestaande teamkennis, eenvoud, documentatie en integratiegemak ook mee. Dit is een teamafweging

--- 

## Considered options

#### Option 1
Java + Spring Boot, RabbitMQ, PostgreSQL, Prometheus + Grafana.
#### Option 2
C# + ASP.NET Core, RabbitMQ, PostgreSQL, Prometheus + Grafana.
#### Option 3
Node.js + Express, zelfgemaakte of externe queue, MongoDB of PostgreSQL, zelfgemaakte monitoring of Grafana-stack.

--- 

## Decision outcomes

### Option 1

### Good, because:
- Omdat Spring Boot goed past bij een zelfstandige backend-applicatie (een los systeem dat apart draait van OpenMRS).
- Spring Boot ondersteunt scheduling (taken automatisch op een bepaald moment uitvoeren), wat goed aansluit op het versturen van notificaties 24 uur en 1 uur van tevoren.
- Spring Boot werkt ook goed samen met messaging tools zoals RabbitMQ.
- RabbitMQ is handig als wachtrij voor berichten, zodat berichten niet direct verloren gaan als er iets misgaat.
- RabbitMQ helpt ook bij foutafhandeling, bijvoorbeeld als een bericht later opnieuw geprobeerd moet worden.
- PostgreSQL past goed, omdat we werken met gegevens, zoals organisaties, afspraken, notificaties en statussen.
- PostgreSQL ondersteunt ook JSON yay.
- Prometheus en Grafana passen goed bij de eis voor monitoring, omdat we daarmee status, prestaties en foutmeldingen zichtbaar kunnen maken.

### Neutral, because:
- Deze stack bestaat uit meerdere onderdelen, zoals een backend, een database, een queue en monitoringtools.
- Betekent dat we meer onderdelen moeten opzetten en met elkaar moeten laten samenwerken.
- Oplossing wordt uitgebreider dan een eenvoudig prototype.

### Bad, because:
- Wij hebben meer uitzoekwerk nodig om Java + Spring Boot goed en netjes in te kunnen richten.
- We moeten ook leren hoe RabbitMQ, PostgreSQL en Prometheus/Grafana samen in de oplossing passen.

### Option 2

### Good, because:
- ASP.NET Core ondersteunt dependency injection (een manier om onderdelen van de applicatie netjes samen te laten werken).
- ASP.NET Core ondersteunt ook background tasks via hosted services (taken die op de achtergrond draaien).
- In combinatie met RabbitMQ, PostgreSQL en Prometheus/Grafana is dit technisch ook een sterke stack.

### Neutral, because:
- Deze optie lijkt technisch bijna net zo goed aan te sluiten op de opdracht als Option 1.
- Het verschil zit daarom niet alleen in de techniek, maar ook in wat wij als team beter kunnen uitleggen en opzetten.

### Bad, because:
- Wij hebben minder ervaring met ASP.NET Core dan met sommige andere opties.
- Meer tijd nodig hebben om deze stack goed op te zetten en te onderbouwen.


### Option 3

### Good, because:
- Node.js is gemaakt voor schaalbare netwerkapplicaties.
- Express maakt het makkelijk om snel een API op te zetten.
- Daardoor vinden wij deze optie aantrekkelijk als we snel iets werkends willen neerzetten.

### Neutral, because:
- Node.js en Express kunnen gebruikt worden voor deze opdracht.
- Wel moeten wij meer keuzes zelf maken over de structuur van de applicatie.
- Zaken zoals scheduling, achtergrondverwerking en de opbouw van de architectuur moeten we dan meer zelf regelen.
- Past ook bij het idee van Express als minimalistisch framework.

### Bad, because:
- Deze optie legt meer verantwoordelijkheid bij ons team om zelf een goede structuur neer te zetten.
- Wij moeten zelf meer keuzes maken over foutafhandeling, uitbreidbaarheid en de inrichting van de applicatie.

--- 

## More information and sources
*SpringBoot:*
- https://docs.spring.io/spring-boot/reference/messaging/amqp.html
- https://docs.spring.io/spring-boot/reference/actuator/enabling.html
- https://docs.spring.io/spring-framework/reference/integration/scheduling.html
*ASP.NET:*
* https://learn.microsoft.com/en-us/aspnet/core/fundamentals/dependency-injection?view=aspnetcore-10.0
* https://learn.microsoft.com/en-us/aspnet/core/fundamentals/host/hosted-services?view=aspnetcore-9.0&tabs=visual-studio
*Node.js:*
- https://nodejs.org/en/about
*Express:*
- https://expressjs.com/
*RabbitMQ:*
- https://www.rabbitmq.com/docs/queues
- https://www.rabbitmq.com/docs/nack
*PostgreSQL:*
- https://www.postgresql.org/docs/current/ddl-constraints.html
- https://www.postgresql.org/docs/current/datatype-json.html
*MariaDB:*
- https://mariadb.com/docs/server/reference/sql-statements/transactions
*MongoDB:*
- https://www.mongodb.com/docs/manual/core/document/
*Prometheus:*
- https://prometheus.io/docs/introduction/overview/
*Grafana:*
- https://grafana.com/docs/grafana/latest/visualizations/dashboards/