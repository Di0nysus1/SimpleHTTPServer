package de.dion.config;

import java.io.File;

import de.dion.config.settings.ConfigSettings;

public class ConfigEntry {
	
	private final String name;
	private String deval;
	private final boolean isString;
	private final String desc;
	private String value;
	
	/**
	 * @param name ist der name der Value, z.B. "translator_lang"
	 * @param defaultValue default value
	 * @param isString ob dieser Value als String in "" gespeichert werden soll
	 * @param desc Beschreibung (wird ueber dem entry in der Config angezeigt)
	 */
	public ConfigEntry(String name, Object defaultValue, boolean isString, String desc) {
		this.name = name;
		this.value = null;
		setDefaultValue(defaultValue);
		this.isString = isString;
		this.desc = this.handeDesc(desc);
	}
	
	private void setDefaultValue(Object deval) {
		if (deval instanceof File) {
            this.deval = ((File) deval).getAbsolutePath();
        } else {
            this.deval = deval.toString();
        }
	}
	
	
	public ConfigEntry(String name, String defaultValue) {
		this(name, defaultValue, true, null);
	}
	
	public ConfigEntry(String name, Object defaultValue) {
		this(name, defaultValue, false, null);
	}
	
	public ConfigEntry(String name, String defaultValue, boolean isString) {
		this(name, defaultValue, isString, null);
	}
	
	public ConfigEntry(String name, Object defaultValue, boolean isString) {
		this(name, defaultValue, isString, null);
	}
	
	public ConfigEntry(String name, String defaultValue, String desc) {
		this(name, defaultValue, true, desc);
	}
	
	public ConfigEntry(String name, Object defaultValue, String desc) {
		this(name, defaultValue, false, desc);
	}
	
	public String handeDesc(String desc) {
		if(desc == null) {
			return null;
		}
		String de = desc;
		for(int i = 0; i < de.length(); i++) {
			int v = de.indexOf("\n", i);
			if(v < 0 || i < 0) {
				break;
			}
			de = de.substring(0, v + 1) + ConfigSettings.com + de.substring(v + 1);
			v = de.indexOf("\n", i);
			i = v + 2;
		}
		return de;
	}
	
    public File getFileValue() {
        return new File(value);
    }
    
    public int getIntValue() {
        return Integer.parseInt(value);
    }
    
    public String getStringValue() {
    	return this.value;
    }

	public String getValue() {
		return getStringValue();
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getName() {
		return name;
	}
	
	public void clear() {
		this.value = null;
	}

	/**
	 * <B>returns the default value!</B>
	 * */
	public String getDefaultValue() {
		return deval;
	}
	
	/**
	 * returnt ob dieses entry eine zahl ist
	 */
	public boolean isString() {
		return isString;
	}
	
	/**
	 * returnt die beschreibung / erklaerung dieses entrys fuer
	 * das config file
	 */
	public String getDesc() {
		return desc;
	}
	
	public boolean hasDesc() {
		return desc != null;
	}

}
