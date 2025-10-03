package de.dion.config;

import java.util.ArrayList;
import java.util.Arrays;

public class Config {
	
	private final String name;
	private String description = null;
	private final ArrayList<ConfigEntry> configEntries;
	
	public Config(String name, ConfigEntry[] configEntries) {
		this.name = name;
		this.configEntries = new ArrayList<ConfigEntry>(Arrays.asList(configEntries));
	}
	
	public String getName() {
		return name;
	}

	public ArrayList<ConfigEntry> getEntrys() {
		return this.configEntries;
	}
	
	public ConfigEntry getEntry(String name) {
		return get(name);
	}
	
	public String getValue(String name) {
		return get(name).getValue();
	}
	
	public void setValue(String name, String value) {
		get(name).setValue(value);
	}
	
	public String getDefault(String name) {
		return get(name).getDefaultValue();
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		if(description.contains("\n")) {
			this.description = description.replace("\n", "\n#");
		} else {
			this.description = description;
		}
	}

	public boolean isEntry(String name) {
		return find(name);
	}
	
	public void clearValues() {
		for(ConfigEntry ee: configEntries) {
			ee.clear();
		}
	}
	
	private boolean find(String name) {
		for(ConfigEntry ee: configEntries) {
			if(ee.getName().equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}
	
	private void addEntry(ConfigEntry e) {
		configEntries.add(e);
	}
	
	private ConfigEntry get(String name) {
		for(ConfigEntry ee: configEntries) {
			if(ee.getName().equalsIgnoreCase(name)) {
				return ee;
			}
		}
		ConfigEntry e = new ConfigEntry(name, "none");
		addEntry(e);
		
		return get(name);
//		throw new EntryNotFoundException("cant find: \"" + name + "\" in \"" + this.name + "\" config | size of the entrys: " + entrys.length);
	}

}
