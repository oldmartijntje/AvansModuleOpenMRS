# Submission Feedback - Marc

- Maak een Proof of concept voor de plugin. 
- Leg vast met elkaar hoe je AI inzet.
- Neem mij bij de ADR mee welke opties uberhaupt geschikt zijn. 

# Aantekeningen Angel
- Duidelijker uitwerken wat de HL7/FHIR ondersteuningen zijn. Bijvoorbeeld welke relevant zijn voor deze scope zoals: appointment, annuleringen, berichtstructuur, validatie en mapping. Wat wordt er wel en niet ondersteunt. 
- Beter uitleggen hoe de oplossing schaalbaar is en welke rol RabbitMQ daarin speelt. Beschrijven hoe RabbitMQ helpt bij asynchrone verwerkingen, buffering, retry en piekbelastingen. Kijken wat er gebeurd al heb je vele OpenMRS-instanties. 
- Beter onderbouwen wat we gaan doen met ActiveMQ. 
- Meer in zicht brengen hoe onze communicatieservice, duidelijk maken wat de service zelf gaat doen: notitifcaties, businesslogica, provider kiezen, logging en monitoring. 
- Aan de hand van de criteria uitwerken waarom we bepaalde tech gebruiken, hoe komt de shortlist tot stand en waarom werkt de ene beter in deze situatie dan andere. 