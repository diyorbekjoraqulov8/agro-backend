package uz.app.agro.rbac;

public final class Permissions {

    public static final String USER_READ =   "user:read";
    public static final String USER_CREATE = "user:create";
    public static final String USER_UPDATE = "user:update";
    public static final String USER_DELETE = "user:delete";

    public static final String ROLE_READ   = "role:read";
    public static final String ROLE_CREATE = "role:create";
    public static final String ROLE_UPDATE = "role:update";
    public static final String ROLE_DELETE = "role:delete";
    public static final String ROLE_ASSIGN = "role:assign";

    public static final String AUDIT_READ = "audit:read";

    public static final String FARMER_READ   = "farmer:read";
    public static final String FARMER_UPDATE = "farmer:update";
    public static final String FIELD_READ    = "field:read";
    public static final String ANALYSIS_READ = "analysis:read";
    public static final String ANALYSIS_RUN  = "analysis:run";

    public static final String CAN_READ_USER   = "hasAuthority('" + USER_READ + "')";
    public static final String CAN_CREATE_USER = "hasAuthority('" + USER_CREATE + "')";
    public static final String CAN_UPDATE_USER = "hasAuthority('" + USER_UPDATE + "')";
    public static final String CAN_DELETE_USER = "hasAuthority('" + USER_DELETE + "')";

    public static final String CAN_READ_ROLE   = "hasAuthority('" + ROLE_READ + "')";
    public static final String CAN_CREATE_ROLE = "hasAuthority('" + ROLE_CREATE + "')";
    public static final String CAN_UPDATE_ROLE = "hasAuthority('" + ROLE_UPDATE + "')";
    public static final String CAN_DELETE_ROLE = "hasAuthority('" + ROLE_DELETE + "')";
    public static final String CAN_ASSIGN_ROLE = "hasAuthority('" + ROLE_ASSIGN + "')";

    public static final String CAN_READ_AUDIT   = "hasAuthority('" + AUDIT_READ + "')";

    public static final String CAN_READ_FARMER   = "hasAuthority('" + FARMER_READ + "')";
    public static final String CAN_UPDATE_FARMER = "hasAuthority('" + FARMER_UPDATE + "')";
    public static final String CAN_READ_FIELD    = "hasAuthority('" + FIELD_READ + "')";
    public static final String CAN_READ_ANALYSIS = "hasAuthority('" + ANALYSIS_READ + "')";
    public static final String CAN_RUN_ANALYSIS  = "hasAuthority('" + ANALYSIS_RUN + "')";

    private Permissions() {}
}
