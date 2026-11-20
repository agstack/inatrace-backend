# Roles and permissions

> **Draft for discussion (#78, #168).** This page describes what the backend **does today** (`main` at
> `f3335b90`), read from the code, role by role. It does not decide what *should* happen: the
> **Intended?** column is for the maintainers to fill in. A row marked "no" becomes an issue with a
> test; a row marked "yes" becomes the policy that tests can rely on. Where a behavior looked
> surprising while writing this page, it is listed under [Open questions](#open-questions).
>
> The per-route matrices in [`api-auth-coverage/`](api-auth-coverage/README.md) say which routes are
> covered by tests; this page says what each role can do. Both should agree.

## Roles

### System roles

Every account has exactly one system role (`User.role`, enum `UserRole`):

| Role | Meaning in the code |
|---|---|
| `USER` | Default role. Everything it can do comes from its company memberships. |
| `REGIONAL_ADMIN` | Administrative role limited to the companies the account is a member of (with some exceptions listed below). |
| `SYSTEM_ADMIN` | Administers the whole instance. |

An account also has a status (`UserStatus`): `UNCONFIRMED` → `CONFIRMED_EMAIL` → `ACTIVE`, or
`DEACTIVATED`. Only `ACTIVE` accounts can sign in.

### Company roles

A membership (`CompanyUser`) links an account to a company with a company role (`CompanyUserRole`):
`COMPANY_USER`, `COMPANY_ADMIN`, `MANAGER` or `ACCOUNTANT`.

**Only `COMPANY_ADMIN` is distinguished by the backend.** `MANAGER` and `ACCOUNTANT` are never
checked, so today they can do exactly what `COMPANY_USER` can (see [Q6](#open-questions)).

A company also has a status (`CompanyStatus`): `REGISTERED`, `ACTIVE` or `DEACTIVATED`.

### How the checks are made

| Mechanism | Rule |
|---|---|
| `@PreAuthorize("hasAuthority(...)")` on a controller method | The caller's **system role** must match. |
| `PermissionsUtil.checkUserIfCompanyEnrolled` | The caller is a member of the company, with any company role. **No exception for system admins.** |
| `PermissionsUtil.checkUserIfCompanyEnrolledOrSystemAdmin` | Member of the company, or system admin. |
| `PermissionsUtil.checkUserIfCompanyEnrolledAndAdminOrSystemAdmin` | Member of the company **and** (company admin **or** system admin). A system admin who is not a member is refused (see [Q7](#open-questions)). |
| `PermissionsUtil.checkUserIfConnectedWithProducts` | Member of a company associated with one of the given products. |
| `PermissionsUtil.checkRegionalAdminIfConnectedWithUser` | The regional admin and the other account share a company (`ACTIVE` or `REGISTERED`). |

In the tables below, **member** means "member of that company with any company role", **admin**
means "`COMPANY_ADMIN` of that company", ✓ means allowed and ✗ refused. Refusals answer
`UNAUTHORIZED` from the services or 403 from `@PreAuthorize`.

## Accounts and users (`/api/user`)

| Action | Route | Anonymous | User | Regional admin | System admin | Intended? |
|---|---|---|---|---|---|---|
| Register, confirm e-mail, request and reset a password, sign in, refresh, sign out | `register`, `confirm_email`, `request_reset_password`, `reset_password`, `login`, `refresh_authentication`, `logout` | ✓ | ✓ | ✓ | ✓ | |
| Read and edit one's own profile (name, surname, language) | `GET/PUT profile` | ✗ | ✓ | ✓ | ✓ | |
| List the users of one's companies | `GET list` | ✗ | ✓ members of the caller's `ACTIVE` companies, **all statuses, including `DEACTIVATED`** | ✓ same | ✓ same | |
| List users as a regional admin | `GET regional-admin/list` | ✗ | ✗ | ✓ users of the caller's `ACTIVE` companies **and users with no company at all**; **excludes `DEACTIVATED`** | ✗ (`hasAuthority('REGIONAL_ADMIN')` only) | |
| List all users | `GET admin/list` | ✗ | ✗ | ✗ | ✓ all users, all statuses | |
| Read another user's profile | `GET admin/profile/{id}` | ✗ | ✗ | ✓ users with no company, or sharing an `ACTIVE`/`REGISTERED` company; the company lists are cut to the shared ones | ✓ any | |
| Edit another user's name, surname and language | `PUT admin/profile` | ✗ | ✗ | ✓ users sharing an `ACTIVE`/`REGISTERED` company; **refused for users with no company** | ✓ any | |
| Confirm another user's e-mail; activate another user | `POST admin/execute/CONFIRM_USER_EMAIL`, `…/ACTIVATE_USER` | ✗ | ✗ | ✓ users with no company, or sharing an `ACTIVE`/`REGISTERED` company | ✓ any | |
| Deactivate another user | `POST admin/execute/DEACTIVATE_USER` | ✗ | ✗ | ✗ | ✓ any | |
| Grant or remove the system admin or regional admin role | `POST admin/execute/SET_USER_SYSTEM_ADMIN`, `UNSET_…`, `SET_USER_REGIONAL_ADMIN`, `UNSET_…` | ✗ | ✗ | ✗ (since #129) | ✓ any | |
| Change one's own status or role | `POST admin/execute/*` with one's own id | ✗ | ✗ | ✗ | ✗ (`INVALID_REQUEST`) | |

The profile returned by `GET admin/profile/{id}` also lists the actions the caller may take on that
account; it follows the same rules (a regional admin is offered only confirm and activate).

In an instance started with the `DEMO` environment, a newly registered account is activated at once
and made `COMPANY_ADMIN` of every active company (`UserService.createUser`).

## Companies and their members (`/api/company`)

| Action | Route | Member | Admin | Regional admin | System admin | Intended? |
|---|---|---|---|---|---|---|
| Create a company (the creator becomes its admin) | `POST create` | ✗ | ✗ | ✓ | ✓ | |
| List one's companies | `GET list` | ✓ | ✓ | ✓ | ✓ | |
| List all companies | `GET admin/list` | ✗ | ✗ | ✗ | ✓ | |
| Read a company: profile, name, onboarding state, members | `GET profile/{id}`, `…/name`, `…/onboardingState`, `…/users` | ✓ | ✓ | ✓ if member | ✓ any | |
| Edit the company profile | `PUT profile` | ✗ | ✓ | ✓ only if also admin of it | ✓ **only if member** | |
| Activate the company | `POST execute/ACTIVATE_COMPANY` | ✗ | ✓ | ✓ if member | ✓ any | |
| Deactivate the company | `POST execute/DEACTIVATE_COMPANY` | ✗ | ✓ | ✗ | ✓ any | |
| Add an account to the company, with any company role | `POST execute/ADD_USER_TO_COMPANY` | ✗ | ✓ any account of the instance, by id | ✓ if member | ✓ any | |
| Change a member's company role | `POST execute/SET_USER_COMPANY_ROLE` | ✗ | ✓ including other admins and oneself | ✓ if member | ✓ any | |
| Remove a member | `POST execute/REMOVE_USER_FROM_COMPANY` | ✗ | ✓ including other admins and oneself | ✓ if member | ✓ any | |
| Merge the company into another one (its members join the other company; this one is deactivated) | `POST execute/MERGE_TO_COMPANY` | ✗ | ✓ only if also admin of the other company (since `11a12506`) | ✗ | ✓ any | |

## Farmers, collectors, plots and company customers (`/api/company`)

All of these are scoped to the company that owns the record.

| Action | Routes | Member | Admin | Regional admin | System admin | Intended? |
|---|---|---|---|---|---|---|
| List, read, add, edit and delete farmers and collectors | `userCustomers/{companyId}/{type}`, `userCustomers/{id}`, `userCustomers/add/{companyId}`, `userCustomers/edit`, `DELETE userCustomers/{id}` | ✓ | ✓ | ✓ if member | ✓ **only if member** | |
| Plots: list the company's, add, refresh a GeoID; upload and export GeoJSON | `userCustomers/{companyId}/plots`, `…/plots/add`, `…/updateGeoID`, `…/uploadGeoData`, `…/exportGeoData` | ✓ | ✓ | ✓ if member | ✓ **only if member** | |
| Export the company's farmers to a spreadsheet | `userCustomers/{companyId}/exportFarmerData` | ✓ | ✓ | ✓ if member | ✓ **only if member** | |
| Import farmers from a spreadsheet, and confirm or separate the plots of an imported farmer | `userCustomers/import/farmers/{companyId}/{documentId}`, `…/confirm-plots`, `…/create-separate` | ✗ | ✗ | ✓ if member | ✓ any | |
| Company customers: list, read, create, edit, delete | `companyCustomers…` | ✓ | ✓ | ✓ if member | ✓ **only if member** | |
| Associations, connected companies, value chains and product types of the company | `associations/{id}`, `{id}/connected-companies`, `{id}/value-chains`, `{id}/product-types` | ✓ | ✓ | ✓ if member | ✓ **only if member** | |

## Supply chain (`/api/chain`)

The route-by-route boundaries are in [`35-supply-chain.md`](api-auth-coverage/35-supply-chain.md).
By role, the pattern is:

- **Members** of the owning company read and write its stock orders, processing orders, payments
  and orders. Companies associated with the same product can read stock and processing records
  (`checkUserIfConnectedWithProducts`).
- **Company admins only** (or a system admin who is a member) can create, edit, deactivate and
  delete facilities and processing actions, and delete processing orders and stock orders
  (`checkUserIfCompanyEnrolledAndAdminOrSystemAdmin`).
- `MANAGER` and `ACCOUNTANT` have no extra rights here either: payments, for example, are open to
  every member.

## Codebooks

The effective policy is in [`38-codebooks.md`](api-auth-coverage/38-codebooks.md#effective-policy).
In short: every authenticated account can read the codebooks; system admins can create, edit and
delete them; regional admins can create entries but not edit existing ones, and cannot delete,
enable or disable them (except enabling a currency); ordinary users cannot change any.

## Products and labels

Pending the #36 matrix ([`36-products-consumer-labels.md`](api-auth-coverage/36-products-consumer-labels.md)).

## Open questions

These came up while reading the code for this page. Each one is "is this intended?":

1. **Deactivated users.** `GET user/list` shows deactivated members to everyone, while
   `GET user/regional-admin/list` hides them from regional admins (#168).
2. **Company status in the regional scope.** The regional user list only counts the regional
   admin's `ACTIVE` companies, while profile, edit and activation checks also count `REGISTERED`
   ones. Should regional admins see the users of `REGISTERED` (or `DEACTIVATED`) companies (#168)?
3. **Accounts without a company.** Any regional admin can list, read, confirm and activate an
   account that belongs to no company, but cannot edit its name. Is a regional admin meant to
   onboard new accounts instance-wide? (#168 reports this as "activate any account".)
4. **Regional admins cannot deactivate**, not even a member of their own company, but they can
   remove that member from the company (#168).
5. **The regional user list refuses system admins**, because it checks
   `hasAuthority('REGIONAL_ADMIN')` only.
6. **`MANAGER` and `ACCOUNTANT`** have no permissions of their own. Should they (for example,
   payments for accountants), or should the roles be documented as labels only?
7. **System admins and membership.** A system admin reads any company profile, but must be a member
   to edit it, to change its facilities or processing actions, and to see its farmers, plots and
   customers. Is membership meant to be required there?
8. **Company admins and other admins.** A company admin can add any account of the instance by id,
   with any role, and can demote or remove other admins, including the last one.
9. **Importing farmers** is limited to system and regional admins: a company admin cannot import
   its own farmers, although it can add them one by one. The import also requires the spreadsheet
   to have been uploaded by the same account.
