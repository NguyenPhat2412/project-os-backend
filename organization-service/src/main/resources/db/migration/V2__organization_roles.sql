alter table organization_memberships
    drop constraint if exists organization_memberships_role_check;

alter table organization_memberships
    add constraint organization_memberships_role_check
        check (role in ('OWNER', 'ADMIN', 'HR', 'DEPARTMENT_MANAGER', 'EMPLOYEE', 'MEMBER'));
