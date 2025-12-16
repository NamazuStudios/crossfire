package dev.getelements.elements.crossfire.api.model;

/**
 * The versions of the Crossfire protocol supported.
 */
public enum Version {

    /**
     * Protocol version 1.0
     */
    V_1_0(1, 0),

    /**
     * Protocol version 1.1
     */
    V_1_1(1, 1);

    private final int major;

    private final int minor;

    Version(final int major, final int minor) {
        this.major = major;
        this.minor = minor;
    }

    /**
     * The major version.
     *
     * @return the major version
     */
    public int getMajor() {
        return major;
    }

    /**
     * The minor version.
     *
     * @return the minor version
     */
    public int getMinor() {
        return minor;
    }

    /**
     * Checks if the given version string matches this version.
     *
     * @param version the version
     * @return the version
     */
    public boolean matches(final String version) {
        return toString().equals(version);
    }

    /**
     * Checks if this version is compatible with the requested version. For clarity, this version must be the server
     * or session version, and the requested version is the client's or message's requested version.
     *
     * @param requested the requested version
     * @return true if compatible, false otherwise
     */
    public boolean isCompatibleWithRequestedVersion(final Version requested) {
        return getMajor() == requested.getMajor() && getMinor() >=  requested.getMinor();
    }

    /**
     * Version 1.0 (String version for DI)
     */
    public static final String VERSION_1_0_NAME = "V_1_0";

    /**
     * Version 1.1 (String version for DI)
     */
    public static final String VERSION_1_1_NAME = "V_1_1";

}
