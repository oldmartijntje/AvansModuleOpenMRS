---

kanban-plugin: board

---

## Todo

- [ ] change ADR 3
- [ ] write pan-bahmni plugin
- [ ] write an ADR on which db we will use
- [ ] Write an ADR on how to handle cancelling of appointments (we already know):
	when we get a cancellation, we don't add it to our database, we push it to the correct openMRS tenant, and wait for the confirmation in the queue to actually remove it from our database
- [ ] Create an ERD
- [ ] make sure to use design patterns like factories with tight or loose coupling, and probably writing another ADR about why we chose it
- [ ] make an UML class diagram for each component
	- [ ] container 1 (see [[avans 2-4 lu1 systeem.svg]])
	- [ ] container 2  (see [[avans 2-4 lu1 systeem.svg]])
	- [ ] container 3  (see [[avans 2-4 lu1 systeem.svg]])
	- [ ]  the bamni.appointment.forwarder


## Doing



## Done

**Complete**
- [x] create a template for the realisatie verantwoording
- [x] create a templete for the adr




%% kanban:settings
```
{"kanban-plugin":"board","list-collapse":[false,false,false]}
```
%%