@startuml
title PRC — Core Class Diagram
left to right direction

skinparam classAttributeIconSize 0
skinparam ArrowColor #555
skinparam ClassBackgroundColor #F9FBFF
skinparam ClassBorderColor #88A
skinparam shadowing false

package "Pricing" {
class PricingEngine {
+FinalizedBill price(TripFacts facts, SelectionInput input)
}
class PricingPlanSelector {
-strategies : List<PlanSelectionStrategy>
+PricingContext select(SelectionInput input)
}
package "<<Strategy>> Plan Selection" {
interface PlanSelectionStrategy {
+Optional<PricingContext> select(SelectionInput input)
}
class MembershipStrategy
class CityStrategy
class EffectiveDateStrategy
}
package "<<Chain of Responsibility>> Pricing Rules" {
abstract class PricingRuleHandler {
#next : PricingRuleHandler
+void apply(PricingContext ctx, TripFacts facts, MutableBill bill)
}
class BaseFeeHandler
class PerMinuteHandler
class EbikeSurchargeHandler
class DiscountHandler
class CapHandler
class TaxHandler
}
class SelectionInput
class PricingContext
class TripFacts
class MutableBill {
-charges : List<ChargeLine>
+void addCharge(ChargeLine line)
+BigDecimal total()
}
class FinalizedBill {
-planVersionId : UUID
-planName : String
-charges : List<ChargeLine>
-total : BigDecimal
}
class ChargeLine {
+code : String
+amount : BigDecimal
+description : String
}

interface PricingPlanVersionRepository

PricingEngine --> PricingPlanSelector : selects
PricingEngine _-- PricingRuleHandler : chainHead
PricingEngine --> TripFacts
PricingEngine --> SelectionInput
PricingEngine --> FinalizedBill : builds
PricingEngine --> PricingContext : uses
PricingPlanSelector o-- PlanSelectionStrategy : aggregates
PricingPlanSelector --> PricingContext : returns
PlanSelectionStrategy --> SelectionInput
PlanSelectionStrategy <|.. MembershipStrategy
PlanSelectionStrategy <|.. CityStrategy
PlanSelectionStrategy <|.. EffectiveDateStrategy
MembershipStrategy --> PricingPlanVersionRepository
CityStrategy --> PricingPlanVersionRepository
EffectiveDateStrategy --> PricingPlanVersionRepository
PricingRuleHandler o--> PricingRuleHandler : next
PricingRuleHandler --> MutableBill : enriches
PricingRuleHandler --> PricingContext
PricingRuleHandler --> TripFacts
MutableBill "1" o-- "_" ChargeLine
FinalizedBill "1" o-- "\*" ChargeLine
PricingRuleHandler <|-- BaseFeeHandler
PricingRuleHandler <|-- PerMinuteHandler
PricingRuleHandler <|-- EbikeSurchargeHandler
PricingRuleHandler <|-- DiscountHandler
PricingRuleHandler <|-- CapHandler
PricingRuleHandler <|-- TaxHandler

note top of PricingPlanSelector #FFF8D2
<<Strategy>> pattern highlighted:
PricingPlanSelector aggregates concrete PlanSelectionStrategy implementations.
end note

note right of PricingRuleHandler #E4F2FF
<<Chain of Responsibility>> pattern highlighted:
Each PricingRuleHandler links to the next handler, forming an ordered rule pipeline.
end note
}

package "Billing" {
class BillingLedgerService {
+LedgerEntry appendTripEntry(FinalizedBill bill, TripFacts facts)
+LedgerEntry appendAdjustment(Long riderId, LedgerEntry original, FinalizedBill bill, String summary)
+List<BillingEntryResponse> getHistory(Long riderId, LocalDateTime from, LocalDateTime to)
}
class LedgerEntry {
+id : Long
+riderId : Long
+planVersionId : UUID
+planName : String
+bikeId : UUID
+startStationId : Long
+endStationId : Long
+startTime : LocalDateTime
+endTime : LocalDateTime
+durationMinutes : long
+distanceKm : double
+total : BigDecimal
+paymentStatus : PaymentStatus
}
class LedgerCharge {
+code : String
+amount : BigDecimal
+meta : Map<String,String>
}
class BillingEntryResponse
interface LedgerEntryRepository
enum PaymentStatus {
PENDING
PAID
FAILED
}

BillingLedgerService --> LedgerEntryRepository
BillingLedgerService --> FinalizedBill
BillingLedgerService --> TripFacts
BillingLedgerService --> LedgerEntry
BillingLedgerService --> BillingEntryResponse
LedgerEntry "1" o-- "\*" LedgerCharge
LedgerEntry --> PaymentStatus
}

package "Payments" {
class PaymentSettlementService {
+PaymentResponse settle(Long riderId, Long ledgerEntryId, String token)
}
interface PaymentGateway {
+PaymentResponse charge(PaymentRequest request)
}
class PaymentRequest {
+riderId : Long
+amount : BigDecimal
+paymentMethodToken : String
+referenceId : String
}
class PaymentResponse {
+success : boolean
+transactionId : String
+processedAt : LocalDateTime
+failureReason : String
}
class FakePaymentGateway

PaymentSettlementService --> LedgerEntryRepository
PaymentSettlementService --> PaymentGateway
PaymentSettlementService --> LedgerEntry
PaymentSettlementService --> PaymentRequest
PaymentSettlementService --> PaymentResponse : returns
PaymentGateway --> PaymentResponse : result
PaymentGateway <|.. FakePaymentGateway
}

package "Disputes" {
class DisputeService {
+DisputeTicketResponse submit(Long riderId, DisputeTicketRequest request)
+DisputeTicketResponse resolve(Long ticketId, DisputeResolutionRequest request)
}
class DisputeTicket {
+id : Long
+ledgerEntryId : Long
+riderId : Long
+status : DisputeStatus
+reason : String
+evidenceUrl : String
+resolutionNote : String
}
class DisputeTicketRequest
class DisputeResolutionRequest
class DisputeTicketResponse
interface DisputeTicketRepository
enum DisputeStatus {
OPEN
APPROVED
REJECTED
}

DisputeService --> DisputeTicketRepository
DisputeService --> LedgerEntryRepository
DisputeService --> BillingLedgerService
DisputeService --> DisputeTicket
DisputeService --> DisputeTicketRequest
DisputeService --> DisputeResolutionRequest
DisputeService --> DisputeTicketResponse
DisputeTicket --> LedgerEntry : references
DisputeTicket --> DisputeStatus
}

BillingLedgerService --> PaymentSettlementService : updates balance

note bottom
Pricing pipeline selects plans via Strategy, pricing rules execute through a Chain of Responsibility,
billing persists immutable ledger entries, payments settle balances, and disputes create adjustments that feed back into billing.
end note

@enduml
