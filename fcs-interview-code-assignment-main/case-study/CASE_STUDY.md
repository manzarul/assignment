# Case Study Scenarios to discuss

## Scenario 1: Cost Allocation and Tracking
**Situation**: The company needs to track and allocate costs accurately across different Warehouses and Stores. The costs include labor, inventory, transportation, and overhead expenses.

**Task**: Discuss the challenges in accurately tracking and allocating costs in a fulfillment environment. Think about what are important considerations for this, what are previous experiences that you have you could related to this problem and elaborate some questions and considerations

**Questions you may have and considerations:**
  * Multiple source systems, no shared identifier (Different data lives in different systems. Example  labor data lives in HR/payroll system , Inventory management in WMS , Transportation is in TMS system etc)
  * Allocation logic should be a configurable rules engine, not hardcoded : some costs map directly to one store/warehouse, others (shared labor, multi-stop routes) are many-to-many and need a defined allocation driver (hours, weight, revenue, etc.) that the business may change over time.
  * Timing and consistency: cost events arrive asynchronously and out of order (e.g., a freight invoice posting days late), so the system needs to handle late-arriving data and define a clear "close"/finalization point for reporting periods.
  * Auditability : since this feeds financial reporting, raw source events should be stored immutably, with allocated costs treated as derived/recomputable data so every number can be traced back to its source.


## Scenario 2: Cost Optimization Strategies
**Situation**: The company wants to identify and implement cost optimization strategies for its fulfillment operations. The goal is to reduce overall costs without compromising service quality.

**Task**: Discuss potential cost optimization strategies for fulfillment operations and expected outcomes from that. How would you identify, prioritize and implement these strategies?

**Questions you may have and considerations:**
 * Labor optimization 
     - Dynamic scheduling based on demand forecasting (align staffing to predicted order volume by hour/day rather than fixed shifts)
     - Cross-training staff to flex between picking, packing, and replenishment based on real-time bottlenecks
   * Network/inventory optimization
     - Re-evaluate warehouse-to-store assignment (are stores served by the nearest/most efficient DC, or by legacy assignment?)
     - Reduce safety stock through better demand forecasting, freeing up storage costs and reducing overstock/markdown losses
   * Transportation optimization
     - Route consolidation and load optimization (fewer, fuller trucks vs. many partial loads)
     - Renegotiate carrier contracts or shift modes (e.g., partial truckload → full truckload thresholds)
    * Overhead/facility optimization
      - Consolidate underutilized warehouse space or renegotiate leases
      - Energy efficiency initiatives (lighting, HVAC) for long-term overhead reduction

## Scenario 3: Integration with Financial Systems
**Situation**: The Cost Control Tool needs to integrate with existing financial systems to ensure accurate and timely cost data. The integration should support real-time data synchronization and reporting.

**Task**: Discuss the importance of integrating the Cost Control Tool with financial systems. What benefits the company would have from that and how would you ensure seamless integration and data synchronization?

**Questions you may have and considerations:**
  * Single source of truth & real-time decisions: without integration, operations and finance work off divergent numbers; integration (event-driven for real-time needs, batch/API sync for periodic close) lets both teams trust the same data.
  * Common data contract : shared identifiers (cost center, GL account mapping, location hierarchy) must be agreed and versioned so data maps cleanly between the Cost Control Tool and financial systems without manual translation.
  * Reconciliation and idempotency : build in automatic reconciliation jobs comparing tool totals against the GL, plus idempotent event handling to avoid double-counting on retries.
  * API-first with monitoring : expose a documented API rather than point-to-point connections, and alert immediately on sync failures/data mismatches so issues surface fast instead of silently corrupting financial reporting.
    

## Scenario 4: Budgeting and Forecasting
**Situation**: The company needs to develop budgeting and forecasting capabilities for its fulfillment operations. The goal is to predict future costs and allocate resources effectively.

**Task**: Discuss the importance of budgeting and forecasting in fulfillment operations and what would you take into account designing a system to support accurate budgeting and forecasting?

**Questions you may have and considerations:**
  * Why Budgeting & Forecasting Matter in Fulfillment
    - Resource planning : labor, warehouse capacity, and transportation all require lead time to scale up/down; accurate forecasts let you staff and provision ahead of demand rather than reactively
    - Capital allocation: decisions like opening a new warehouse, investing in automation, or expanding a fleet depend on multi-year cost/volume projections
    - Variance accountability : budgets give operations teams a baseline to measure against, surfacing when actual costs deviate and why (volume-driven vs. inefficiency-driven)
    - Seasonality management : fulfillment costs swing heavily with peak periods (holidays); forecasting lets the business plan temp labor, extra transportation capacity, and inventory positioning ahead of time
  * Historical data as the foundation
  * Driver-based forecasting, not just trend extrapolation 
    - Costs should be modeled against underlying business drivers (order volume, unit count, new store openings) rather than simple historical trend lines, so forecasts adjust when the business changes (e.g., a new store opening mid-year)
    - This requires the system to model relationships (e.g., "labor cost = f(order volume, hourly rate, productivity rate)") rather than treating cost as a flat time series
  * Continuous reforecasting vs. static annual budget
    - A once-a-year budget quickly becomes stale; consider rolling forecasts (e.g., updated monthly/quarterly) that incorporate actuals as they come in, alongside the fixed annual budget used for financial commitments
    - This means the system needs to cleanly separate "budget" (a fixed commitment/baseline) from "forecast" (a living, updated projection) — conflating them causes confusion
   * Integration with actuals
     - Forecasts are only useful if compared against actuals regularly (variance analysis); this ties back to Scenario 3's integration work — the same cost data pipeline feeding financial systems should feed the budgeting/forecasting tool
   #### Questions
     * What forecast granularity does the business actually need — network-level, warehouse-level, or SKU/category-level?
     * How should the system handle structural changes (new warehouse, discontinued store) that break historical trend continuity?
     * Who owns forecast assumptions (finance sets growth assumptions, ops sets productivity assumptions) — does the system need role-based assumption inputs?
     * What's the acceptable forecast error tolerance, and how is that measured?
     * Should the system flag forecast/actual variances automatically above a threshold, or is this a manual review process?       

## Scenario 5: Cost Control in Warehouse Replacement
**Situation**: The company is planning to replace an existing Warehouse with a new one. The new Warehouse will reuse the Business Unit Code of the old Warehouse. The old Warehouse will be archived, but its cost history must be preserved.

**Task**: Discuss the cost control aspects of replacing a Warehouse. Why is it important to preserve cost history and how this relates to keeping the new Warehouse operation within budget?

**Questions you may have and considerations:**
 * Business Unit Code reuse creates a continuity trap
   - Since the new warehouse reuses the old one's BU code, any system that keys cost history by BU code alone will blend old and new warehouse data into one continuous series. this breaks trend analysis, budget baselines, and variance reporting unless explicitly handled
   - The system needs a way to distinguish "same BU code, different physical entity" , typically an additional dimension like a warehouse instance ID, facility ID, or effective-date range tied to the BU code
 * Cost history preservation matters for multiple reasons
    - Audit/compliance : historical financial postings tied to the old warehouse must remain traceable even after archival (can't just delete records)  
    - Budget baseline continuity — if the new warehouse's budget is derived from the old warehouse's historical run-rate (a common practice), that history must remain accessible and clearly attributed to the old facility, not silently merged into the new one's actuals
    - Root cause / trend analysis — if someone investigates a cost anomaly a year from now, they need to know whether they're looking at old-warehouse or new-warehouse data
  * New warehouse budget setup is a discontinuity, not a trend continuation
    - A new facility typically has different cost drivers (different lease terms, new equipment depreciation schedule, potentially different labor market/rates, possibly higher automation) — so its budget shouldn't be a simple extrapolation of the old warehouse's numbers
    - However, the old warehouse's historical data is still useful as a reference point (expected volume, seasonality patterns) — just not as a direct baseline for cost-per-unit assumptions
  * Transition period cost tracking
     - Warehouse replacements often have a transition/ramp-up period (parallel run, phased migration) where both facilities may have costs simultaneously, or where the new facility is operating below full efficiency
     - This period needs its own budget treatment (expect higher cost-per-unit temporarily) rather than being judged against steady-state targets
   #### Key Design Considerations
     * Separate the "logical BU" from the "physical facility" — model this as BU code + facility ID + effective date range, so historical data always resolves unambiguously to the correct physical warehouse
     * Archive, don't delete — the old warehouse's records move to an archived/read-only state but remain queryable for audit and historical reference
     * Explicit cutover date — define a clear "as-of" date after which all new cost events attribute to the new facility under the reused BU code
     * Budget reset with documented linkage — the new warehouse gets its own budget built from its own cost structure, with a documented note/reference to which historical warehouse it replaces (for context, not for direct calculation)
     * Reporting continuity for stakeholders — dashboards that show "BU code X performance over time" need to visually or structurally indicate the facility change (e.g., an annotation on the trend line) so viewers don't misinterpret a discontinuity as a performance swing
   #### Questions
     * Is there an overlap/transition period where both warehouses are operational, and how should shared costs (e.g., transferred inventory, shared staff) be attributed during that window?
     * What's the source of truth for "this BU code = old warehouse before date X, new warehouse after" — is this a config table, a slowly-changing dimension, or something else?
     * Should the new warehouse's initial budget be zero-based, or derived (with adjustments) from the old warehouse's run-rate?          

## Instructions for Candidates
Before starting the case study, read the [BRIEFING.md](BRIEFING.md) to quickly understand the domain, entities, business rules, and other relevant details.

**Analyze the Scenarios**: Carefully analyze each scenario and consider the tasks provided. To make informed decisions about the project's scope and ensure valuable outcomes, what key information would you seek to gather before defining the boundaries of the work? Your goal is to bridge technical aspects with business value, bringing a high level discussion; no need to deep dive.
