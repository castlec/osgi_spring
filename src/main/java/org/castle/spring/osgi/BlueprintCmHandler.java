package org.castle.spring.osgi;

import java.net.URL;

import org.apache.aries.blueprint.ext.impl.ExtNamespaceHandler;

public class BlueprintCmHandler extends ExtNamespaceHandler {
	
	
	
	public static final String BLUEPRINT_CM_NAMESPACE_V1_0 = "http://aries.apache.org/blueprint/xmlns/blueprint-cm/v1.0.0";
    public static final String BLUEPRINT_CM_NAMESPACE_V1_1 = "http://aries.apache.org/blueprint/xmlns/blueprint-cm/v1.1.0";
    public static final String BLUEPRINT_CM_NAMESPACE_V1_2 = "http://aries.apache.org/blueprint/xmlns/blueprint-cm/v1.2.0";
    
	@Override
	public URL getSchemaLocation(String namespace) {
		if(namespace.contains("blueprint-cm/v")) {
			return super.getSchemaLocation(namespace.replace("blueprint-cm/v", "blueprint-ext/v"));
		}
		else {
			return super.getSchemaLocation(namespace);
		}
	}
	
	

}
