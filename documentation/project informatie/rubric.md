# LU1 Applicatie Integratie - Groepsopdracht

**Deadline:** 26 mei 2026 23:59

## Voorwaarden voor presentatie

De volgende onderdelen zijn vereist om deel te kunnen nemen aan de presentatie:

1. **Code repository** - Lever de code in die je downloadt vanuit je repository, zonder eventueel geïmporteerde libraries of gegenereerde temporary files.

2. **README.md** - Code repository bevat een readme.md waarin staat beschreven hoe de oplossing kan worden gestart inclusief voorbeeld requests.

3. **ADR Directory** - Code repository bevat een ADR directory met markdown files van ADR's:
   - De eerste is de verantwoording voor een apart component zoals aangegeven in de workshop
   - Een ADR over de observability stack

4. **Realisatie transparantie logboek** - Met daarin de gebruikte tools, en bij gebruik van AI-tooling relevante voorbeelden (prompts)

5. **Visualisaties** - Van de systeem- en applicatiearchitectuur en het bedrijfsproces

## Rubric: LU1 Applicatie-integratie Beroepsproduct v1.0

| Criterium | Onvoldoende (0 pt) | Voldoende (10 pt) | Goed (20 pt) | Score |
|-----------|-------------------|------------------|-------------|-------|
| **Architectuur-beschrijving & bedrijfsprocessen** | De projectgroep kan onvoldoende toelichten hoe bepaalde (niet) functionele requirements terugkomen in het ontwerp. | De projectgroep kan de functional en non-functional requirements toelichten op basis van het ontwerp en toont waar nodig code-voorbeelden ter illustratie. | Bevat alle onderdelen van "Voldoende" en de projectgroep licht overwogen alternatieven toe en beschrijft op basis van welke criteria deze zijn afgevallen. | / 20 |
| **Betrouwbaarheid** | De schaalbaarheid en robuustheid van de oplossing kan onvoldoende worden toegelicht of bewezen. | De schaalbaarheid en robuustheid kan worden toegelicht op basis van een failure-mode effect analysis die overeenkomt met de opgeleverde code en architectuur en kan daarnaast worden bewezen met behulp van een performancerapportage en realtime monitoring van de huidige staat. | Bevat alle onderdelen van "Voldoende" en de projectgroep toont aan welke test- en verbeterstappen hebben plaats gevonden om de performance en robuustheid te verbeteren. | / 20 |
| **Duurzaam ontwerp** | De persistentiemechanismen zijn niet conform best practices t.a.v. toekomstbestendigheid geïntegreerd. De implementatie biedt geen ruimte voor multi-tenancy of voor andere bedrijfsprocessen om aan te sluiten op het gerealiseerde proces. | De oplossing is bestand tegen wijzigingen doordat versiebeheer is toegepast en/of er rekening is gehouden met schemawijzigingen. De implementatie volgt ontwerpprincipes impliciet zodat multi-tenancy en andere bedrijfsprocessen in de toekomst gefaciliteerd kunnen worden. | Bevat alle onderdelen van "Voldoende" en houdt daarnaast rekening met uitzonderingsscenario's. De projectgroep onderbouwt expliciet uitbreidbaarheid aan de hand van ontwerpprincipes. | / 20 |
| **Testresultaten** | Er zijn geen unit-, integratie- en/of systeemtesten aanwezig, of de kwaliteit daarvan is onvoldoende om de werking van het systeem te valideren. | Er zijn unit-tests aanwezig die de werking valideren. Er zijn geautomatiseerde tests aanwezig waarmee de werking en de basis betrouwbaarheid lokaal kan worden aangetoond. | Bevat alle onderdelen van "Voldoende" en daarnaast zijn er additionele testmethodieken gehanteerd die de werking van onderbouwde uitzonderingsscenario's en/of de kwaliteit op additionele kenmerken (security, architectuur) geautomatiseerd valideren. | / 20 |
| **Realisatie verantwoording** | Beschrijft welke tools (IDE's, Coding Agents of tools) zijn gebruikt en waarom, maar zonder diepgaande reflectie op de toegevoegde waarde of kosten. | Beschrijft welke tools zijn gebruikt, waarom, en reflecteert op de toegevoegde waarde (bijv. tijdwinst, kwaliteit) en kosten (bijv. iteraties, debugtijd). | Bevat alle onderdelen van "Voldoende" en biedt concrete voorbeelden en verbeterpunten die bij toekomstige soortgelijke projecten kunnen worden gehanteerd. | / 20 |
| **TOTAAL** | | | | **/ 100** |

## Beoordelingschaal

| Beoordeling | Minimale score |
|-------------|----------------|
| Onvoldoende | 0 punten |
| Voldoende | 55 punten |
| Goed | 100 punten |

**Opmerking:** De beoordeling wordt toegepast op alle leden van de groep.