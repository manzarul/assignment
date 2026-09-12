# Questions

Here we have 3 questions related to the code base for you to answer. It is not about right or wrong, but more about what's the reasoning behind your decisions.

1. In this code base, we have some different implementation strategies when it comes to database access layer and manipulation. If you would maintain this code base, would you refactor any of those? Why?

**Answer:**
```txt
    I see couple of problems in long terms maintainance 
     - violation of layered Architectural design patterns . (If you see your controller is directly binding with DB layer )
     - Directly exposing DB layer as a contract , which will be very hard when u want to update any details in DB or try to migrated DB model  (We need to have DAO and DTO concepts)
     - Introduce service layer and make sure controller will connect with service and service layer will have business validation 
     - Manual field-by-field copying in update(). Small thing, but once Product grows past 4-5 fields this turns into a maintenance headache and a spot where someone forgets to add a new field to the copy logic. A mapper (MapStruct, or even just a constructor/with method) removes that whole class of bug.
     - Error handling : The ErrorMapper is fine as a safety net, but relying on WebApplicationException with hardcoded string messages scattered through the resource means your error responses aren't consistent or localizable, and there's no error code taxonomy. I'd introduce typed exceptions (ProductNotFoundException, InvalidProductException) and let the mapper translate those to proper HTTP codes — the resource stops doing if/throw string-message plumbing.
```
----
2. When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded directly everything. What would be your thoughts about what are the pros and cons of each approach and what would be your choice?

**Answer:**
```txt
    I would lean towards code-first. Spec-first has the benefit of defining the contract upfront and generating code/docs from the OpenAPI YAML, but in the Warehouse API the generated code feels a bit clunky and can be harder to maintain when the spec changes.

    With code-first, like Product and Store, we have more control and the code is easier to understand and maintain. We can still generate the OpenAPI documentation from the code, so I don't see documentation as a strong reason to prefer spec-first anymore.

    My preference would be to standardize on **code-first with OpenAPI generated from the code**, rather than having different approaches across the services.


```
----
3. Given the need to balance thorough testing with time and resource constraints, how would you prioritize and implement tests for this project? Which types of tests would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**
```txt
    I would focus first on **unit tests** for the core business logic, as they give us good coverage without taking too much time to maintain. Then I’d add **integration tests** for important areas such as database, Redis, and external API interactions, since those are the places where issues are more likely to happen between components.
    
    For the critical end-to-end flows, I’d have a smaller number of **E2E tests** rather than trying to cover everything.

    I’d also make sure tests run as part of the CI/CD pipeline so new changes don't reduce coverage or break existing functionality. Over time, I’d review coverage and add tests whenever we fix a bug or introduce an important new scenario. I’d focus more on **meaningful coverage of critical paths** rather than simply trying to achieve a high percentage.

```