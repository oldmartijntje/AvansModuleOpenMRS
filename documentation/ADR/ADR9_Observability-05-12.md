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
De communicatiemodule bestaat uit drie zelfstandige Spring Boot applicaties die via RabbitMQ en een gedeelde database met elkaar communiceren. Een notificatiejob raakt alle drie containers: container 1 ontvangt het event, container 2 plant de notificatie, container 3 verstuurt hem. Zonder gecentraliseerde observability is het onmogelijk om te achterhalen wat er is misgegaan als een notificatie niet aankomt, waar de vertraging zit als het systeem traag is, of welke container verantwoordelijk is voor een fout.

De niet-functionele eisen stellen dat de werking van de communicatiemodule volledig inzichtelijk moet zijn via monitoringtooling op basis van OpenTelemetry, en dat er een real-time dashboard beschikbaar moet zijn waarop OpenMRS-beheerders de status van berichten, throughput en foutmeldingen kunnen volgen.

De centrale vraag is welke observability stack wordt gebruikt, hoe de drie pilaren (logs, metrics, traces) worden geïnstrumenteerd, en hoe trace context wordt doorgegeven tussen de drie containers via RabbitMQ zodat één notificatiejob als één samenhangende trace zichtbaar is.

---
## Considered Options
### Optie A: Hosted observability platform (Datadog, New Relic)
Een extern gehost platform dat logs, metrics en traces ontvangt en visualiseert.

Voordelen:
- Snel op te zetten zonder eigen infrastructuur
- Uitgebreide dashboards en alerting out of the box
- Schaal mee zonder configuratie

Nadelen:
- Patiëntdata en medische afspraakdata verlaat de eigen infrastructuur en belandt op servers van derden
- Kosten lopen snel op bij hogere volumes
- Vendor lock-in: instrumentatiecode raakt gekoppeld aan het specifieke platform
- Onaanvaardbaar gezien de privacyvereisten rondom medische data in dit project

### Optie B: Zelfgebouwde LGTM stack
De LGTM-stack van Grafana Labs combineert Loki voor logs, Grafana Tempo voor traces, Prometheus voor metrics, en Grafana voor visualisatie. Alle componenten draaien als Docker containers op de eigen VPS. De applicaties exporteren telemetry via de OpenTelemetry standaard naar een lokale OpenTelemetry Collector die de data distribueert naar de juiste backend.

Voordelen:
- Alle telemetry data blijft op de eigen infrastructuur, geen patiëntdata naar externe partijen
- OpenTelemetry is vendor-neutraal: instrumentatiecode is niet gekoppeld aan een specifiek platform
- De volledige stack is beschikbaar als Docker Compose setup wat aansluit op de bestaande infrastructuur
- Loki, Tempo en Prometheus zijn volledig integreerbaar in één Grafana dashboard
- Gratis en open source

Nadelen:
- Vereist meer configuratie dan een hosted oplossing
- De stack voegt meerdere extra containers toe aan Docker Compose
- Vereist beheer van retentieperiodes en opslagcapaciteit op de VPS

---
## Decision Outcome
**Gekozen: Optie B, Zelfgebouwde LGTM stack**

**Justification**
Een hosted observability platform is onaanvaardbaar omdat medische patiëntdata de eigen infrastructuur niet mag verlaten. De LGTM stack biedt alle benodigde functionaliteit volledig on-premise en sluit direct aan op de bestaande Docker Compose infrastructuur. OpenTelemetry als instrumentatiestandaard voorkomt vendor lock-in: de applicatiecode is gekoppeld aan de OpenTelemetry API, niet aan Loki, Tempo of Prometheus specifiek. Als in de toekomst een component van de stack wordt vervangen hoeft de instrumentatiecode in de applicaties niet te worden aangepast.

---
## De drie pilaren

#### Logs
Alle drie containers schrijven gestructureerde logs in JSON formaat via de OpenTelemetry Java agent. Logs bevatten altijd de volgende velden: timestamp, severity, traceId, spanId, containerName, tenantId, appointmentUuid en een beschrijvend message veld. Patiëntidentificatoren zoals patientName en phoneNumber worden nooit gelogd conform ADR 8.

Logs worden via de OpenTelemetry Collector doorgestuurd naar Loki. In Grafana zijn logs doorzoekbaar op tenantId, appointmentUuid, containerName en traceId. De traceId in een log is klikbaar in Grafana en opent direct de bijbehorende trace in Tempo.

#### Metrics
Alle drie containers exposen metrics via de OpenTelemetry Java agent en de Micrometer Spring Boot integratie. Prometheus scraped deze metrics elke 15 seconden.

De volgende metrics worden bijgehouden voor het Grafana dashboard:
Voor container 1: aantal ontvangen appointment events per minuut uitgesplitst per tenantId en eventType, aantal verwerkingsfouten per minuut, en de verwerkingstijd per event.

Voor container 2: aantal aangemaakte notificatiejobs per minuut uitgesplitst per notificationType (24H of 1H), en het aantal afspraken in PENDING_CANCELLATION status ouder dan 5 minuten.

Voor container 3: aantal verstuurde notificaties per minuut uitgesplitst per provider en tenantId, aantal mislukte provider-aanroepen per minuut, gemiddelde latency per provider-aanroep, en het aantal berichten in `notification.dlq`.

#### Traces
Traces volgen één notificatiejob van het moment dat de OpenMRS plugin een event publiceert tot het moment dat container 3 de bevestiging van de provider ontvangt. Een trace bestaat uit de volgende spans: plugin publiceert event naar RabbitMQ, container 1 ontvangt en verwerkt het event, container 1 schrijft naar de database, container 2 leest de database en publiceert een notificatiejob, container 3 ontvangt de notificatiejob en roept de provider aan.

Traces worden opgeslagen in Grafana Tempo en zijn doorzoekbaar op traceId, tenantId en appointmentUuid.

---
## Context propagation tussen containers

Omdat de drie containers via RabbitMQ communiceren en niet via directe HTTP aanroepen moet de trace context expliciet worden doorgegeven via RabbitMQ berichtheaders volgens de W3C Trace Context standaard.

Wanneer container 1 een notificatiejob publiceert naar `notification.queue` injecteert het de W3C traceparent header in de berichtheaders. De traceparent header heeft het formaat `00-{traceId}-{parentSpanId}-{flags}`, bijvoorbeeld `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`. De traceId blijft ongewijzigd door alle containers heen zodat de volledige keten als één trace zichtbaar is in Tempo.

Wanneer container 2 het bericht van `notification.queue` consumeert extraheert het de traceparent header en continueert de trace. Wanneer container 2 een nieuw notificatiejob publiceert naar de interne queue injecteert het de bijgewerkte traceparent met zijn eigen spanId als nieuwe parentId. Container 3 herhaalt dit patroon.

In Spring AMQP wordt context propagation geïmplementeerd via een `MessagePostProcessor` bij het publiceren en een `ChannelAwareMessageListener` bij het consumeren die de OpenTelemetry context extraheren en injecteren.

---
## Grafana Dashboard
Het real-time dashboard voor OpenMRS-beheerders toont de volgende panelen:
Notificatiestatus: een tabel met de meest recente notificaties uitgesplitst per tenantId met de status (verstuurd, mislukt, in wachtrij) en de bijbehorende appointmentUuid.

Throughput: een tijdreeksgrafiek van het aantal verwerkte notificaties per minuut over de afgelopen 24 uur uitgesplitst per provider.

Foutrate: een tijdreeksgrafiek van het aantal mislukte provider-aanroepen per minuut met een drempelwaarde alerting als de foutrate boven 5 procent komt.

Dead-letter queue: een getal dat het huidige aantal berichten in `notification.dlq` toont met een rood signaal als dit groter dan nul is.

Provider latency: een histogram van de gemiddelde responstijd per provider over de afgelopen 24 uur.

PENDING_CANCELLATION timeout: een tabel van afspraken die langer dan 5 minuten in PENDING_CANCELLATION status staan.

---
## Consequences

Good, because:
- Alle telemetry data blijft op de eigen infrastructuur en verlaat nooit de VPS
- OpenTelemetry instrumentatie is vendor-neutraal en niet gekoppeld aan Loki, Tempo of Prometheus specifiek
- De traceId koppelt logs, metrics en traces aan elkaar zodat een beheerder vanuit een foutmelding in het dashboard direct de volledige trace kan openen
- Context propagation via W3C Trace Context maakt de volledige keten van één notificatiejob door alle drie containers zichtbaar als één samenhangende trace

Neutraal, because:
- W3C Trace Context propagation via RabbitMQ berichtheaders moet expliciet worden geïmplementeerd in alle drie containers; dit is gedocumenteerd als implementatieaandachtspunt
- Retentieperiodes voor logs en traces moeten worden geconfigureerd op de VPS om opslagcapaciteit te beheersen; standaard wordt 30 dagen aangehouden voor traces en 90 dagen voor logs

Bad, because:
- De LGTM stack voegt meerdere extra containers toe aan Docker Compose wat de opstartcomplexiteit vergroot
- Prometheus scraping elke 15 seconden genereert extra netwerkverkeer tussen de containers; bij hoge load kan dit worden verhoogd naar 30 seconden

---
## More Information
Implementatieaandachtspunten:
- De OpenTelemetry Java agent wordt als JVM argument meegegeven aan alle drie Spring Boot applicaties via de Docker Compose environment configuratie: `-javaagent:/otel-agent.jar`
- De OpenTelemetry Collector draait als aparte Docker container en ontvangt telemetry op poort 4317 (gRPC) en 4318 (HTTP)
- De Collector is geconfigureerd met drie exporters: Loki voor logs, Tempo voor traces, en Prometheus voor metrics
- Grafana draait als Docker container en is bereikbaar op poort 3000 via het interne Docker netwerk
- W3C Trace Context propagation wordt geïmplementeerd via `TextMapPropagator` uit de OpenTelemetry Java API in combinatie met Spring AMQP `MessagePostProcessor`
- De service name per container wordt geconfigureerd via de environment variabele `OTEL_SERVICE_NAME` met waarden `inbound-processor`, `scheduler` en `notification-worker`
- Zie ADR 6 voor de drie-container architectuur waarop deze observability stack van toepassing is
- Zie ADR 8 voor de logging restricties rondom patiëntdata die ook van toepassing zijn op de telemetry pipeline