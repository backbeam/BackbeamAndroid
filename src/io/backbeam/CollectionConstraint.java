package io.backbeam;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CollectionConstraint {
	
	private List<String> ids = new ArrayList<String>();
	
	public CollectionConstraint addObject(BackbeamObject object) {
		if (object.getId() != null) {
			ids.add(object.getId());
		}
		return this;
	}
	
	public CollectionConstraint addObjects(List<BackbeamObject> objects) {
		for (BackbeamObject obj : objects) {
			addObject(obj);
		}
		return this;
	}
	
	public CollectionConstraint addIdentifier(String identifier) {
		if (identifier != null) {
			ids.add(identifier);
		}
		return this;
	}
	
	public CollectionConstraint addIdentifiers(List<String> identifiers) {
		for (String identifier : identifiers) {
			if (identifier != null) {
				ids.add(identifier);
			}
		}
		return this;
	}
	
	private void addIdentifier(String identifier, String prefix) {
		ids.add(prefix+identifier);
	}
	
	private void addIdentifiers(List<String> identifiers, String prefix) {
		for (String identifier : identifiers) {
			ids.add(prefix+identifier);
		}
	}
	
	public void addTwitterIdentifier(String identifier) {
		addIdentifier(identifier, "tw:");
	}
	
	public void addTwitterIdentifiers(List<String> identifiers) {
		addIdentifiers(identifiers, "tw:");
	}
	
	public void addFacebookIdentifier(String identifier) {
		addIdentifier(identifier, "fb:");
	}
	
	public void addFacebookIdentifiers(List<String> identifiers) {
		addIdentifiers(identifiers, "fb:");
	}
	
	public void addEmailAddress(String identifier) {
		addIdentifier(identifier, "email:");
	}
	
	public void addEmailAddresses(List<String> identifiers) {
		addIdentifiers(identifiers, "email:");
	}
	
	public void addGooglePlus(String identifier) {
		addIdentifier(identifier, "gp:");
	}
	
	public void addGooglePlus(List<String> identifiers) {
		addIdentifiers(identifiers, "gp:");
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
	    Iterator<String> iter = ids.iterator();
	    if (iter.hasNext())
	        sb.append(iter.next());
	    while (iter.hasNext()) {
	        sb.append("\n");
	        sb.append(iter.next());
	    }
	    return sb.toString();
	}

}
