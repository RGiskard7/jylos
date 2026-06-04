package com.example.jylos.plugin;

/**
 * Optional base class for external/built-in plugins with shared metadata defaults.
 */
public abstract class AbstractPlugin implements Plugin {

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public String getAuthor() {
        return "";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public String[] getDependencies() {
        return new String[0];
    }
}
