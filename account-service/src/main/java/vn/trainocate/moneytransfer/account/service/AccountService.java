package vn.trainocate.moneytransfer.account.service;

/**
 * @deprecated Refactored to CQRS pattern.
 * <ul>
 *   <li>Write operations → {@link vn.trainocate.moneytransfer.account.command.AccountCommandService}
 *   <li>Read operations  → {@link vn.trainocate.moneytransfer.account.query.AccountQueryService}
 * </ul>
 */
@Deprecated(since = "CQRS refactor", forRemoval = true)
public class AccountService {
    // Intentionally empty — logic moved to AccountCommandService and AccountQueryService
}
