@startuml
title PRC — Combined Activity (Return → Bill → Pay / Export / Dispute)

skinparam ArrowColor #555
skinparam ActivityBackgroundColor #F9FBFF
skinparam ActivityBorderColor #88A
skinparam linetype ortho
skinparam shadowing false

|Trip Service (Stations/Apps)|
start
:Bike returned at dock;
:Emit TripCompleted event;

|PRC System|
:Accept TripCompleted event;
:Fetch trip facts (start/end,\nstations, bikeId, isEbike, riderId);
:Resolve effectiveDate & membership;

note right
<<Strategy>>
Select PricingPlanVersion (planVersionId)
based on effectiveDate, city, membership
end note

:Select PricingPlanVersion (planVersionId);

note right
<<Chain of Responsibility>>
Execute pricing handlers in order:

1. BaseFeeHandler
2. PerMinuteHandler
3. EbikeSurchargeHandler
   (optional) Discount/Cap/Tax
   end note

:Apply pricing rules deterministically;
:Generate TripSummary;
:Create immutable LedgerEntry\n(with planVersionId & breakdown);
:Update Rider pending balance;
:Send Trip Summary notification;

|Notification Service|
:Deliver trip summary;

' -------- Independent post-billing flows (can occur anytime, selectively) --------
|PRC System|
if (Rider opens billing?) then (yes)
|Rider|
:Open Billing History;

|PRC System|
:Retrieve ledger & trip summaries;
:Render billing items\n(start/end, bike, stations, charges, total, status);
endif

|PRC System|
if (Rider chooses to pay?) then (yes)
|Rider|
:Click "Pay now";

|PRC System|
if (Balance > 0?) then (yes)
:Create PaymentIntent;
else (no)
:Show "No payment due";
endif

|External Payment Gateway|
:Authorize / confirm payment;

|PRC System|
if (Payment confirmed?) then (yes)
:Mark related ledger entries as PAID;
:Generate receipt (PDF);
:Send receipt notification;
else (no)
:Show payment failed status;
endif

|Notification Service|
:Deliver receipt / status;
endif

|PRC System|
if (Admin exports ledger?) then (yes)
|Admin|
:Select export date range;

|PRC System|
:Collect matching ledger entries;
:Generate CSV export;
:Provide download;
endif

|PRC System|
if (Rider disputes a trip?) then (yes)
|Rider|
:Submit dispute for a trip;

|PRC System|
:Create DisputeTicket;

|Admin|
:Review trip & station logs, evidence;
if (Approved?) then (yes)
:Approve adjustment;
else (no)
:Reject dispute;
endif

|PRC System|
if (Approved?) then (yes)
:Compute adjustment;
:Post Adjustment LedgerEntry\n(immutable; original not altered);
:Send dispute resolution (Approved);
else (no)
:Send dispute resolution (Rejected);
endif

|Notification Service|
:Deliver resolution notice;
endif

stop
@enduml
