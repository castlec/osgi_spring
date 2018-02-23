package org.castle.spring.osgi;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

import javax.xml.stream.EventFilter;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.aries.blueprint.NamespaceHandler;
import org.apache.aries.blueprint.container.BlueprintContainerImpl;
import org.apache.aries.blueprint.container.SimpleNamespaceHandlerSet;
import org.apache.aries.blueprint.reflect.BeanMetadataImpl;
import org.codehaus.stax2.ri.evt.NamespaceEventImpl;
import org.osgi.service.blueprint.reflect.BeanArgument;
import org.osgi.service.blueprint.reflect.BeanProperty;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.Metadata;
import org.osgi.service.blueprint.reflect.RefMetadata;
import org.osgi.service.blueprint.reflect.ValueMetadata;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.config.RuntimeBeanNameReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;

@Configuration
public class OsgiToSpringContextPostProcessor implements BeanFactoryPostProcessor, ResourceLoaderAware {

	private static final boolean DEBUG = false;

	private static Resource[] getBlueprintNamespaceResources(ResourcePatternResolver resourcePatternResolover) throws IOException {
		return resourcePatternResolover.getResources(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "/META-INF/blueprint.handlers");
	}

	private static Resource[] getBlueprintResources(ResourcePatternResolver resourcePatternResolover) throws IOException {
		Resource[] resources = resourcePatternResolover.getResources(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "/OSGI-INF/blueprint/blueprint.xml");
		Resource[] ext_resources = resourcePatternResolover.getResources(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "/OSGI-INF/blueprint/blueprint-ext.xml");
		if(false&& ext_resources.length > 0) {
			Resource[] resources_ = new Resource[resources.length + ext_resources.length];
			System.arraycopy(resources, 0, resources_, 0, resources.length);
			System.arraycopy(ext_resources, 0, resources_, resources.length, ext_resources.length);
			resources = resources_;
		}
		return resources;
	}

	private static BlueprintContainerImpl loadOsgiFromResources(Resource[] resources, Resource[] namespaceResources,
			ClassLoader classLoader) throws IOException, ClassNotFoundException, InstantiationException,
			IllegalAccessException, URISyntaxException, Exception {
		List<URL> urls = new ArrayList<URL>(resources.length);
		for(Resource r: resources) {
			urls.add(r.getURL());
		}
		String[] namespaces = loadNamespaces(resources);
		SimpleNamespaceHandlerSet s = new SimpleNamespaceHandlerSet() {

			@Override
			public void addNamespace(URI namespace, URL schema, NamespaceHandler handler) {
				System.out.println("namespace: " + namespace.toString() + " " + schema.toString() + " " + handler.getClass().getName());
				super.addNamespace(namespace, schema, handler);
			}};
		for (Resource r: namespaceResources) {
			loadNamespace(s, r, namespaces);
		}
		BlueprintContainerImpl impl = new BlueprintContainerImpl(classLoader, urls, null, s, true);
		Set<String> componentIds = impl.getComponentIds();
		if(DEBUG)dumpContents(impl, componentIds, System.out);
		return impl;
	}

	private static void dumpContents(BlueprintContainerImpl impl, Set<String> componentIds, PrintStream out) {
		for(String componentId : componentIds) {
			out.println(componentId);
			Object component = impl.getComponentInstance(componentId);
			out.println(component);
		}
	}
	
	private static String[] loadNamespaces(Resource ...resources ) {
		if (resources == null) {
			return new String[0];
		}
		
		XMLInputFactory newInstance = XMLInputFactory.newInstance();
		final Set<String> namespaces = new HashSet<String>();
		for (Resource r : resources) {
			try {
				XMLEventReader streamReader = newInstance.createXMLEventReader(r.getInputStream());
//				XMLEventReader filteredReader = newInstance.createFilteredReader(streamReader, new EventFilter() {
//
//					@Override
//					public boolean accept(XMLEvent event) {
//						return event.isNamespace();
//					}});
//				XMLEvent e = null;
				streamReader.forEachRemaining(new Consumer<XMLEvent>() {

					@Override
					public void accept(XMLEvent t) {
						if(t.getEventType() == XMLEvent.START_ELEMENT) {
							StartElement n = (StartElement) t;
							System.out.println(n.getName());
							Iterator namespaceURI = n.getNamespaces();
							while(namespaceURI.hasNext()) {
								Namespace namespace =  (Namespace) namespaceURI.next(); 
								namespaces.add(namespace.getNamespaceURI());
							}
						}
					}
				});
			} catch (XMLStreamException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//			newFactory.
		}
		return namespaces.toArray(new String[namespaces.size()]);
	}

	private static void loadNamespace(SimpleNamespaceHandlerSet s, Resource r, String[] allNamespaces) throws IOException,
			ClassNotFoundException, InstantiationException, IllegalAccessException, URISyntaxException {
		Properties p = new Properties();
		p.load(r.getInputStream());
		Set<Object> keySet = p.keySet();
		String[] handlers = new String[keySet.size()];
		keySet.toArray(handlers);
		for (String handler: handlers) {
			Class c;
			c = OsgiToSpringContextPostProcessor.class.getClassLoader().loadClass(handler);
			NamespaceHandler h = (NamespaceHandler) c.newInstance();
			String[] namespaces = getNamespacesFromClass(handler, c);
			if(namespaces == null) {
				namespaces = allNamespaces;
			}
			for (String namespace : namespaces) {
				URL schemaLocation = h.getSchemaLocation(namespace);
				if (DEBUG) {
					System.out.println(namespace);
					System.out.println(schemaLocation);
				}
				if (schemaLocation != null) {
					s.addNamespace(new URI(namespace), schemaLocation, h);
				}
			}
			if (DEBUG) {
				System.out.println(h.getClass().getName());
			}
		}
	}
	
	//TODO change this to use reflections so it won't fail in older versions of aries. 
	//loader will use default namespaces as provided by first pass with parser.
	private static String[] getNamespacesFromClass(String handler, Class c) {
		
		String[] namespaces = null;
		try {
			c = OsgiToSpringContextPostProcessor.class.getClassLoader().loadClass(handler);
			NamespaceHandler h = (NamespaceHandler) c.newInstance();
			org.apache.aries.blueprint.Namespaces annotation = (org.apache.aries.blueprint.Namespaces) c
					.getAnnotation(org.apache.aries.blueprint.Namespaces.class);
			if(annotation != null) {
				namespaces = annotation.value();
			}
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return namespaces;
		
	}
	private ResourceLoader resourceLoader;
	
	private static void cloneBlueprintToSpringContext(ConfigurableListableBeanFactory beanFactory, 
			BlueprintContainerImpl blueprint) {
		Set<String> componentIds = blueprint.getComponentIds();
		for(String componentId : componentIds) {
			Object component = blueprint.getComponentInstance(componentId);
			if(component.getClass().getName().contains("blueprint")) {
				System.out.println("skipping component " + componentId + " " + component.getClass().getName());
				continue;
			}
			ComponentMetadata componentMetadata = blueprint.getComponentMetadata(componentId);
			
			if (componentMetadata instanceof BeanMetadataImpl) {
				BeanMetadataImpl metadata = (BeanMetadataImpl) componentMetadata;
				//find out the class type
				String beanClass = metadata.getClassName();
				if(beanClass == null && metadata.getRuntimeClass() != null) {
					beanClass = metadata.getRuntimeClass().getName();
				}
				else {
					beanClass = component.getClass().getName();
				}
				
				BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(beanClass);
				beanDefinitionBuilder.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_NAME);
				
				//load the constructor for the thing from the metadata
				ConstructorArgumentValues constructorArgumentValues = new ConstructorArgumentValues();
				MutablePropertyValues propertyValues = new MutablePropertyValues();
				List<BeanProperty> osgiProperties = metadata.getProperties();
				HashMap<String, Class> references = new HashMap<String, Class>();
				for(BeanProperty osgiProperty : osgiProperties) {
					String name = osgiProperty.getName();
					Metadata value = osgiProperty.getValue();
					if(value instanceof RefMetadata) {
						RefMetadata ref = (RefMetadata) value;
						ref.getComponentId();
						references.put(ref.getComponentId(), blueprint.getComponentInstance(ref.getComponentId()).getClass());
						//we're referencing another component that should exist
						beanDefinitionBuilder.addPropertyReference(name, ref.getComponentId());
					}
					if(value instanceof ValueMetadata) {
						ValueMetadata v = (ValueMetadata) value;
						String stringValue = v.getStringValue();
						String type = v.getType();
						propertyValues.addPropertyValue(new PropertyValue(name, stringValue));
						beanDefinitionBuilder.addPropertyValue(name, stringValue);
					}
				}
				List<BeanArgument> arguments = metadata.getArguments();
				for(int i = 0; i <arguments.size(); i++) {
					//we've got arguments so we have to wire by constructor instead of by type
					BeanArgument argument = arguments.get(i);
					String valueType = argument.getValueType();
					Metadata value = argument.getValue();
					String name = null;
					ValueHolder valueHolder = null;
					if(value instanceof RefMetadata) {
						RefMetadata ref = (RefMetadata) value;
						//we don't want to lose typing where we have it but need to overwrite 
						//the ones that are just object as they will cause ambiguity errors.
						if(Object.class.getName().equals(valueType)){
							valueType = blueprint.getComponentInstance(ref.getComponentId()).getClass().getName();
						}
						name = ref.getComponentId();
						valueHolder = new ValueHolder(null, valueType, name);
						
						references.put(name, blueprint.getComponentInstance(ref.getComponentId()).getClass());
						constructorArgumentValues.addIndexedArgumentValue(i, new RuntimeBeanNameReference(name));
						beanDefinitionBuilder.addConstructorArgReference(name);
					}
					else if (value instanceof ValueMetadata){
						ValueMetadata valueAsValue = (ValueMetadata) value;
						valueHolder = new ValueHolder(null, valueType, name);
						constructorArgumentValues.addIndexedArgumentValue(i, valueHolder);
						beanDefinitionBuilder.addConstructorArgValue(valueAsValue.getStringValue());
					}
				}
				beanDefinitionBuilder.setInitMethodName(metadata.getInitMethod());

				beanDefinitionBuilder.setScope(BeanDefinition.SCOPE_SINGLETON);
				((BeanDefinitionRegistry) beanFactory).registerBeanDefinition(componentId, beanDefinitionBuilder.getBeanDefinition());
			}
		}
		System.out.println("destroying cloned osgi container");
		blueprint.destroy();
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		try {
			BlueprintContainerImpl blueprint = loadOsgiFromResources(getBlueprintResources((ResourcePatternResolver) resourceLoader), 
								  getBlueprintNamespaceResources((ResourcePatternResolver) resourceLoader), 
								  beanFactory.getBeanClassLoader());
			cloneBlueprintToSpringContext(beanFactory, blueprint);
		} catch (Exception e) {
			throw new ApplicationContextException("failed to translate osgi context", e);
		}
		
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}


}
