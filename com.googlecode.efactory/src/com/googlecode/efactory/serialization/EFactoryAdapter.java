/*******************************************************************************
 * Copyright (c) 2013 Michael Vorburger.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Michael Vorburger - initial API and implementation
 ******************************************************************************/
package com.googlecode.efactory.serialization;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EContentAdapter;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.concurrent.IUnitOfWork;
import org.eclipse.xtext.util.concurrent.IWriteAccess;

import com.google.inject.Provider;
import com.googlecode.efactory.building.NoNameFeatureMappingException;
import com.googlecode.efactory.eFactory.Feature;
import com.googlecode.efactory.eFactory.NewObject;
import com.googlecode.efactory.eFactory.Value;
import com.googlecode.efactory.resource.EFactoryResource;

/**
 * EMF Adapter (Observer) which get notified by the observed derived "real"
 * EObjects so that it can "re-sychronize" changes made to them to the source
 * EFactory model.
 * 
 * This is in the serialization package because, while it has nothing to do with
 * Serialization per se, it can use a lot of the infrastructure in this package.
 * Serialization is actually simply a more "coarse grained" (entire NewObject,
 * not just individual features of [possibly sub-] NewObjects) application of
 * the same "re-sychronization" requirement.
 * 
 * @author Michael Vorburger
 */
public class EFactoryAdapter extends EContentAdapter {
	private static Logger logger = Logger.getLogger(EFactoryAdapter.class);
	
	// Provider<> is used to keep this lazy - at the time this is constructed, it might not be available, yet
	protected final Provider<IWriteAccess<XtextResource>> writeAccessProvider;

	public EFactoryAdapter(Provider<IWriteAccess<XtextResource>> writeAccessProvider) {
		this.writeAccessProvider = writeAccessProvider;
	}

	@Override
	public void notifyChanged(Notification msg) {
		super.notifyChanged(msg); // MUST do first

		if (msg.isTouch())
			return;
		
		final NewObject newObject = findChangedNewObject(msg);
		if (newObject == null)
			return;
		
		final Feature factoryFeature = findChangedFactoryFeature(msg, newObject);
		if (factoryFeature == null)
			return;
		
		try {
			switch (msg.getEventType()) {
				case Notification.SET :
					setValue(factoryFeature, msg);
					break;
			}
		} catch (NoNameFeatureMappingException e) {
			logger.warn("NoNameFeatureMappingException in notifyChanged() handling for: " + msg.toString(), e);
		}
	}

	// setOrAddValue ?
	protected void setValue(final Feature factoryFeature, final Notification msg) throws NoNameFeatureMappingException {
		FactoryBuilder factoryBuilder = null; // TODO ???
		// TODO does the reading here have to be done inside an IUnitOfWork as well?! Even if we already have the Feature in hand? Why? @see http://koehnlein.blogspot.ch/2010/06/semantic-model-access-in-xtext.html
		final EAttribute eAttribute = (EAttribute) factoryFeature.getEFeature();
		FeatureBuilder builder = AttributeBuilder.attribute(eAttribute, factoryBuilder).value(msg.getNewValue());
		final Value newEFValue = builder.createValue(); // OR: builder.build(); // if the Features doesn't exist yet..
		
		// do NOT just: factoryFeature.setValue(newEFValue); // @see http://koehnlein.blogspot.ch/2010/06/semantic-model-access-in-xtext.html
		final String uriFragment = factoryFeature.eResource().getURIFragment(factoryFeature);
		writeAccessProvider.get().modify(new IUnitOfWork.Void<XtextResource>() {
			@Override
			public void process(XtextResource resource) throws Exception {
				final Feature localFactoryFeature = (Feature) resource.getEObject(uriFragment);
				localFactoryFeature.setValue(newEFValue);
			}
		});
	}
		
	protected Feature findChangedFactoryFeature(final Notification msg, final NewObject newObject) {
		final Object changedFeature = msg.getFeature();
		final EStructuralFeature changedEFeature = (EStructuralFeature) changedFeature;
		final EList<Feature> newObjectAllFeatures = newObject.getFeatures();
		// TODO could use org.eclipse.xtext.util.SimpleCache<Key, Value> to cache result of this search loop 
		for (Feature feature : newObjectAllFeatures) {
			if (changedEFeature.equals(feature.getEFeature())) {
				return feature;
			}
		}
		// TODO this is wrong actually - it has to work for features which are not yet in the Factory, too.. so create them on the fly, if needed @see com.googlecode.efactory.builder.resync.tests.BuilderResyncTest.testSetNewFeature()
		return null;
	}

	protected NewObject findChangedNewObject(Notification msg) {
		final Object notifier = msg.getNotifier();
		final EObject eNotifier = (EObject) notifier;
		final Resource eNotifierResource = eNotifier.eResource();
		final EFactoryResource eNotifierFactoryResource = (EFactoryResource) eNotifierResource;  
		final NewObject newObject = eNotifierFactoryResource.getEFactoryElement(eNotifier);
		return newObject;
	}
	
}
