package dev.getelements.elements.crossfire.model;

/**
 * The versions of the Crossfire protocol supported.
 */
public enum Version {

    V_1_0(1, 0);

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

}
