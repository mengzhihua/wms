---
name: wms-browser-testing
description: Run local WMS browser workflows with isolated H2 data and meaningful state/role assertions.
---

# Local setup
- Identify the process listening on 8080 before starting the backend. Stop only the stale WMS process, not a broad process-name pattern that might match the calling shell.
- Stop the backend before moving `backend/data` to a timestamped backup. H2 schema updates may require a fresh database; preserve existing data instead of deleting it.
- This machine has JDK8 at `/home/ubuntu/tools/jdk8u504-b01`, Maven at `/home/ubuntu/tools/apache-maven-3.9.9/bin`, and Node24 at `/home/ubuntu/.nvm/versions/node/v24.19.0/bin`.
- Start Spring Boot from `backend`, with JAVA_HOME and PATH set appropriately. If all dependencies are cached and repositories are unavailable, use `mvn -q -o spring-boot:run`.
- Start Vite from `frontend` using `npm run dev -- --host 0.0.0.0`. Browser URL is `http://localhost:5173`; `/api` proxies to 8080.
- Fresh demo login is `admin / admin123`. Create disposable OPERATOR and VIEWER users via system user management.

# Browser assertions
- Use actual receipt and putaway to create a known inventory quantity. Avoid running the complete smoke seed during an arithmetic-sensitive UI test unless its stock/order mutations are recorded.
- Count-plan chain: draft -> submit -> approve -> generate tasks -> count -> recount rounds. If a confirmed recount still differs, generate and approve an adjustment before completion; otherwise complete directly (adjustment generation is rejected when there is no final mismatch). Assert completion rejects pending tasks, pending recounts, and unapproved/rejected adjustments separately.
- Do not infer a count lock from the stock availability label. Attempt a small move/adjustment and verify rejection, then verify the same operation is possible after deletion/cancellation/completion releases the lock.
- Build strategy test orders with matching quantities and allocated stock, but no active wave. Preview must not create waves; re-execution should not rematch existing waved orders.
- Package duplicate protection may be implemented by excluding PACKED orders from the PICKED-order selector. Record this as UI exclusion, not proof of direct API rejection.
- Preserve a draft plan, open shortage, and NEW package for role checks. Completed/shipped records alone cannot prove write actions are role-restricted.
- OPERATOR warehouse writes are permitted; VIEWER is read-only. Master-data and user administration are ADMIN-only. Verify actual business mutations as OPERATOR as well as button visibility.
- Wide tables may require horizontal scrolling to reveal operations. Confirm a delete popover before judging disappearance; refresh the list before declaring deletion broken.
- Capture browser console/network from the beginning with preservation across navigation. Separate expected business validation from uncaught errors and library deprecation warnings.

## Devin Secrets Needed
None for fresh local demo data. Do not reuse demo credentials against a shared deployment without authorization.
