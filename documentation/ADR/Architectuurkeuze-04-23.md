---
status: Accepted
date: 2026-04-23
deciders: angel, chris, martijn
---

# AD: zelfstandige module vs. ingebouwde module

## Context and Problem Statement

De communicatiemodule moet notificaties versturen voor meerdere OpenMRS-organisaties wereldwijd. De opdrachtgever wil dit als SaaS aanbieden, wat betekent dat één instantie van de module meerdere OpenMRS-installaties moet kunnen bedienen.

## Considered Options

### Optie A: Ingebouwde OpenMRS module

**Voordelen:**
- Directe integratie met OpenMRS API's en data models
- Geen extra netwerkverkeer tussen systemen
- Gebruikers hoeven geen afzonderlijke service op te zetten
- Makkelijker om patiëntdata direct uit OpenMRS te lezen

**Nadelen:**
- Elke OpenMRS-organisatie moet de module apart installeren en beheren
- Niet geschikt voor SaaS-model (geen gedeelde instantie mogelijk)
- Schaalbaarheidsproblemen: elke organisatie draait eigen instance
- Upgrades en maintenance zijn nodig per organisatie
- Inconsistente versies en configuraties tussen organisaties
- Moeilijker om kosten/usage per organisatie te traceren voor SaaS-billing
- Afhankelijk van OpenMRS versie van elke organisatie

### Optie B: Zelfstandige module

**Voordelen:**
- Perfect voor SaaS-model: één centrale instantie voor meerdere organisaties
- Schaalbaar: kan duizenden organisaties bedienen
- Eenvoudigere deployment en maintenance (één server)
- Organisaties hoeven geen extra software te installeren
- Makkelijk om per organisatie te billing/monitoren
- Onafhankelijk van OpenMRS versie van klanten
- Kan worden gehost in de cloud (onze eigen persoonlijke VPS)
- Betere uptime en reliability management

**Nadelen:**
- Meer complexiteit in integratie (REST API's, webhooks)
- Netwerkverkeer tussen OpenMRS en module
- Moet multi-tenancy veilig implementeren
- Afhankelijkheid op netwerkverbinding
- Moet credentials veilig beheren per organisatie
- Meer verantwoordelijkheid voor beveiliging (centraal systeem)

## Decision Outcome

**Gekozen: Optie B - Zelfstandige module**

### Justification

Het SaaS-model vereist een zelfstandige, gedeelde instantie. Dit is de enige optie die:
1. Meerdere organisaties tegelijk kan bedienen
2. Schaalbaar is voor wereldwijd gebruik
3. Eenvoudig onderhoud en updates mogelijk maakt
4. Usage-based billing mogelijk maakt
5. Geen installatie bij organisaties vereist

### Consequences

**Good, because:**
- Organisaties kunnen direct met de service aan de slag zonder installatie
- Centraal beheer van security patches en updates
- Cost-effective: geen redundante infrastractuur nodig
- Eenvoudig to scale: extra capaciteit toevoegen zonder klanten te beïnvloeden
- Betere monitoring en analytics van alle transacties

**Neutral, because:**
- Vereist robust API-design tussen OpenMRS en module
- Vereist multi-tenancy security controls
- Netwerklatency tussen systemen kan optreden

**Bad, because:**
- Centraal punt van failure: downtime treft alle organisaties
- Vereist sterke encryptie voor multi-tenant data-isolation
- Hogere beveiligingsvereisten (PII/medische data)
- Afhankelijk van netwerkverbinding tussen systemen
- Meer complexe deployment vs. ingebouwde module

## More information

**Implementatieaandachtspunten:**
- REST API's voor communicatie tussen OpenMRS en module
- Multi-tenant data-isolation op database-niveau
- Per-organisatie API-keys voor authenticatie
- Webhook-support voor event-driven notificaties
- Audit logging van alle transacties per organisatie