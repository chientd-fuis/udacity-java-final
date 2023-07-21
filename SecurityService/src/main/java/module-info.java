module com.udacity.catpoint.security {
    requires com.udacity.catpoint.image;
    requires com.google.common;
    requires com.google.gson;

    requires java.sql;
    requires java.desktop;
    requires java.prefs;
    requires miglayout;

    opens com.udacity.catpoint.security.data to com.google.gson;
}