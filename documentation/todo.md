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