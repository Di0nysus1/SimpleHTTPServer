package de.dion.config;

import de.dion.config.exceptions.EntryNotFoundException;
import de.dion.config.exceptions.WrongFileTypeException;
import de.dion.config.settings.ConfigSettings;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;

public class ConfigHelper implements ConfigSettings {
	
	private final Config c;
	private File configFile;
	
	public ConfigHelper(Config config, String path) throws WrongFileTypeException {
		this(config);
		if(!path.endsWith(fileending)) {
			throw new WrongFileTypeException(path + " is not a \"" + fileending + "\" file type");
		}
		this.configFile = new File(path);
	}
	
	public ConfigHelper(Config config, File path) throws WrongFileTypeException {
		this(config);
		if(!path.getPath().endsWith(fileending)) {
			throw new WrongFileTypeException(path + " is not a \"" + fileending + "\" file type");
		}
		this.configFile = path;
	}
	
	private ConfigHelper(Config config) {
		this.c = config;
	}
	
	public File getConfigFile() {
		return this.configFile;
	}
	
	public Config getConfig() {
		return c;
	}
	
	
	public String getName() {
		return c.getName();
	}

	public ArrayList<ConfigEntry> getEntrys() {
		return c.getEntrys();
	}
	
	public ConfigEntry getEntry(String name) {
		return c.getEntry(name);
	}
	
	public String getValue(String name) {
		return c.getValue(name);
	}
	
	public boolean getBooleanValue(String name) {
		if(getValue(name).toLowerCase().equals("true")) {
			return true;
		} else if(getValue(name).toLowerCase().equals("false")) {
			return false;
		} else {
			throw new IllegalAccessError(name + " -> \"" + getValue(name) + "\" is not a boolean!");
		}
	}
	
	public int getIntValue(String name) {
		return Integer.parseInt(getValue(name));
	}
	
	public void setValue(String name, String value) {
		c.setValue(name, value);
	}
	
	public void setValue(String name, Object value) {
		c.setValue(name, value.toString());
	}
	
	public String getDefault(String name) {
		return c.getDefault(name);
	}
	
	public boolean isEntry(String name) {
		return c.isEntry(name);
	}
	
	public void clearValues() {
		c.clearValues();
	}
	
	public void read() {
		if(configFile.exists() && configFile.isFile()) {
			this.readconfig();
		} else {
			//konnte keine config finden,
			//erstelle neue.
			this.createNew();
		}
	}
	
	private void readconfig(){
		boolean edited = false;
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), "UTF-8"));
			String l = null;
			clearValues();
				while((l = br.readLine()) != null) {
					try {
						String g = l;
						l = l.trim();
						if(l.startsWith(com)) {
							continue;
						} if(l.equals("")) {
							continue;
						}
						
						String[] sp = g.split(":");
						String r = "";
						ConfigEntry e = c.getEntry(sp[0]);
						for(int i = 1; i < sp.length; i++) {
							if(i > 1) {
								r += ":";
							}
							r += sp[i];
						}
						r = r.trim();
						if(e.isString()) {
							// wenn string field entferne "" zeichen vorne und hinten und replae \" durch "
							r = r.replace("\\\"", "\"");
							r = r.substring(1, r.length() - 1);
							r = r.trim();
							e.setValue(r);
						} else if(!r.equals("")) {
							e.setValue(r);
						}
						
						//debug msg
//						System.out.println(e.getName() + " " + e.getValue());
					} catch(EntryNotFoundException e1) {
						System.out.println(e1.getMessage());
						edited = true;
					}
				}
			
			for(ConfigEntry ee: c.getEntrys()) {
				if(ee.getValue() == null) {
					ee.setValue(ee.getDefaultValue());
					if(!edited) {
						edited = true;
					}
				}
			}
			br.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if(edited) {
			createNewWithValues();
		}
	}
	
	public void saveValues() {
		if(configFile.exists() && configFile.isFile()) {
			this.save();
		} else {
			//konnte keine config finden,
			//erstelle neue mit aktuellen values!
			this.createNewWithValues();
		}
	}
	
	private void save() {
		try {
			//config auslesen
			LinkedList<String> lines = new LinkedList<>();
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), "UTF-8"));
			String line = "";
			
			while((line = br.readLine()) != null) {
				lines.add(line);
			}
			br.close();
			
			//values setten und config saven
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile, false), Charset.forName("UTF-8")));
			for(int i = 0; i < lines.size(); i++) {
				String ll = lines.get(i);
				
				if(ll.equals("") || ll.startsWith(com)) {
					bw.write(ll);
					bw.newLine();
					continue;
				}
				String[] split = ll.split(":");
				String name = split[0].trim();
				ConfigEntry ee = c.getEntry(name);
				
				if(!ee.isString()) {
					bw.write(name + ": " + ee.getValue());
				} else {
					bw.write(name + ": \"" + ee.getValue() + "\"");
				}
				if(i != lines.size() - 1) {
					bw.newLine();
				}
			}
			bw.close();
			
		} catch(IOException e1) {
			e1.printStackTrace();
		}
	}
	
	private void createNewWithValues() {
		configFile.delete();
		for(ConfigEntry ee: c.getEntrys()) {
			if(ee.getValue() == null) {
				ee.setValue(ee.getDefaultValue());
			}
		}
		try {
			configFile.createNewFile();
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile, false), Charset.forName("UTF-8")));
			bw.write(com + "Config File: " + c.getName());
			if(c.getDescription() != null) {
				bw.newLine();
				bw.write(com + c.getDescription());
			}
			bw.newLine();
			bw.newLine();
			bw.newLine();
			boolean n = true;
			boolean b = false;
			for(ConfigEntry ee: c.getEntrys()) {
				bw.newLine();
				if(b) {
					bw.newLine();
				}
				if(ee.hasDesc()) {
					if(!n && !b) {
						bw.newLine();
					}
					bw.write(com + ee.getDesc());
					bw.newLine();
				}
				if(!ee.isString()) {
					bw.write(ee.getName() + ": " + ee.getValue());
				} else {
					bw.write(ee.getName() + ": \"" + ee.getValue() + "\"");
				}
				b = false;
				if(ee.hasDesc()) {
					b = true;
				}
				n = false;
			}
			
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void createNew() {
		configFile.delete();
		clearValues();
		try {
			configFile.createNewFile();
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile, false), Charset.forName("UTF-8")));
			bw.write(com + "Config File: " + c.getName());
			if(c.getDescription() != null) {
				bw.newLine();
				bw.write(com + c.getDescription());
			}
			bw.newLine();
			bw.newLine();
			bw.newLine();
			boolean n = true;
			boolean b = false;
			for(ConfigEntry ee: c.getEntrys()) {
				bw.newLine();
				if(b) {
					bw.newLine();
				}
				if(ee.hasDesc()) {
					if(!n && !b) {
						bw.newLine();
					}
					bw.write(com + ee.getDesc());
					bw.newLine();
				}
				if(!ee.isString()) {
					bw.write(ee.getName() + ": " + ee.getDefaultValue());
				} else {
					bw.write(ee.getName() + ": \"" + ee.getDefaultValue() + "\"");
				}
				b = false;
				if(ee.hasDesc()) {
					b = true;
				}
				n = false;
			}
			
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.readconfig();
	}
	
	public void refresh() {
		this.read();
	}
	
}
