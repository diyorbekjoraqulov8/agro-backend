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
    public static final String FIELD_WRITE   = "field:write";
    public static final String ANALYSIS_READ = "analysis:read";
    public static final String ANALYSIS_RUN  = "analysis:run";

    public static final String BILLING_READ   = "billing:read";
    public static final String BILLING_MANAGE = "billing:manage";

    public static final String FEEDBACK_READ  = "feedback:read";
    public static final String FEEDBACK_REPLY = "feedback:reply";
    public static final String FEEDBACK_MANAGE = "feedback:manage";

    public static final String FILE_READ   = "file:read";
    public static final String FILE_UPLOAD = "file:upload";
    public static final String FILE_MANAGE = "file:manage";

    public static final String SETTINGS_READ   = "settings:read";
    public static final String SETTINGS_MANAGE = "settings:manage";
    public static final String REFERRAL_READ   = "referral:read";

    public static final String ONBOARDING_READ   = "onboarding:read";
    public static final String ONBOARDING_MANAGE = "onboarding:manage";

    public static final String NOTIFICATION_READ = "notification:read";
    public static final String NOTIFICATION_SEND = "notification:send";
    public static final String ADVISOR_READ      = "advisor:read";
    public static final String PROBLEMS_READ     = "problems:read";
    public static final String PROBLEMS_MANAGE   = "problems:manage";

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
    public static final String CAN_WRITE_FIELD   = "hasAuthority('" + FIELD_WRITE + "')";
    public static final String CAN_READ_ANALYSIS = "hasAuthority('" + ANALYSIS_READ + "')";
    public static final String CAN_RUN_ANALYSIS  = "hasAuthority('" + ANALYSIS_RUN + "')";
    // Dala tarixi tahlil natijalarini ham ko'rsatadi — ikkala huquq
    public static final String CAN_READ_FIELD_HISTORY = CAN_READ_FIELD + " and " + CAN_READ_ANALYSIS;

    public static final String CAN_READ_BILLING   = "hasAuthority('" + BILLING_READ + "')";
    public static final String CAN_MANAGE_BILLING = "hasAuthority('" + BILLING_MANAGE + "')";

    public static final String CAN_READ_FEEDBACK  = "hasAuthority('" + FEEDBACK_READ + "')";
    public static final String CAN_REPLY_FEEDBACK = "hasAuthority('" + FEEDBACK_REPLY + "')";
    public static final String CAN_MANAGE_FEEDBACK = "hasAuthority('" + FEEDBACK_MANAGE + "')";

    public static final String CAN_READ_FILE   = "hasAuthority('" + FILE_READ + "')";
    public static final String CAN_UPLOAD_FILE = "hasAuthority('" + FILE_UPLOAD + "')";
    public static final String CAN_MANAGE_FILE = "hasAuthority('" + FILE_MANAGE + "')";

    public static final String CAN_READ_SETTINGS   = "hasAuthority('" + SETTINGS_READ + "')";
    public static final String CAN_MANAGE_SETTINGS = "hasAuthority('" + SETTINGS_MANAGE + "')";
    public static final String CAN_READ_REFERRAL   = "hasAuthority('" + REFERRAL_READ + "')";

    public static final String CAN_READ_ONBOARDING   = "hasAuthority('" + ONBOARDING_READ + "')";
    public static final String CAN_MANAGE_ONBOARDING = "hasAuthority('" + ONBOARDING_MANAGE + "')";

    public static final String CAN_READ_NOTIFICATION = "hasAuthority('" + NOTIFICATION_READ + "')";
    public static final String CAN_SEND_NOTIFICATION = "hasAuthority('" + NOTIFICATION_SEND + "')";
    public static final String CAN_READ_ADVISOR      = "hasAuthority('" + ADVISOR_READ + "')";
    public static final String CAN_READ_PROBLEMS     = "hasAuthority('" + PROBLEMS_READ + "')";
    public static final String CAN_MANAGE_PROBLEMS   = "hasAuthority('" + PROBLEMS_MANAGE + "')";

    private Permissions() {}
}
