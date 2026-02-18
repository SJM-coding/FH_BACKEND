# Upgrade Notes

## 2026-02-18
- Removed `tournament_applications` feature entirely (domain/service/controller/repository/DTO + migration).
- `tournament_participants` is now the only participation/registration system.
- `V1__baseline.sql` updated to drop `tournament_applications` table/indexes.
- Deleted `V9__create_tournament_application_tables.sql`.

## Follow-up
- If you need application/approval flow, extend `tournament_participants` with status + approval timestamps instead of reintroducing `tournament_applications`.

## Extension Plan: Application/Approval via tournament_participants
- Add columns:
  - `status` values: PENDING, APPROVED, REJECTED, CANCELED
  - `applied_at`, `approved_at`, `rejected_at`, `cancelled_at`
  - `reviewed_by` (user id) for approval/rejection audit
- API flow:
  - POST `/api/tournaments/join` => create PENDING
  - POST `/api/tournaments/participants/{id}/approve`
  - POST `/api/tournaments/participants/{id}/reject`
  - POST `/api/tournaments/participants/{id}/cancel`
- Permissions:
  - applicant can cancel (PENDING/APPROVED depending on rules)
  - organizer/captain can approve/reject
- Constraints:
  - unique (tournament_id, team_id)
  - max teams check should count only APPROVED
