package de.dion.config;

import java.io.File;

import de.dion.config.settings.ConfigSettings;

public class Entry {
	
	private final String name;
	private String deval;
	private final boolean isString;
	private final String desc;
	private String value;
	
	/**
	 * @param name ist der name der Value, zb. "translator_lang"
	 * @param deval default value
	 * @param isString ob diese Value als String in "" gespeichert werden soll
	 * @param desc Beschreibung (wird über dem entry in der Config angezeigt)
	 */
	public Entry(String name, Object deval, boolean isString, String desc) {
		this.name = name;
		this.value = null;
		setDefaultValue(deval);
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
	
	
	public Entry(String name, String deval) {
		this(name, deval, true, null);
	}
	
	public Entry(String name, Object deval) {
		this(name, deval, false, null);
	}
	
	public Entry(String name, String deval, boolean isString) {
		this(name, deval, isString, null);
	}
	
	public Entry(String name, Object deval, boolean isString) {
		this(name, deval, isString, null);
	}
	
	public Entry(String name, String deval, String desc) {
		this(name, deval, true, desc);
	}
	
	public Entry(String name, Object deval, String desc) {
		this(name, deval, false, desc);
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
        File out = new File(value);
        return out;
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
	 * returnt die beschreibung / erklärung dieses entrys für
	 * das config file
	 */
	public String getDesc() {
		return desc;
	}
	
	public boolean hasDesc() {
		return desc != null;
	}

}
