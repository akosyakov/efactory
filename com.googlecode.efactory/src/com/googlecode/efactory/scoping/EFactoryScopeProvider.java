/*******************************************************************************
 * Copyright (c) 2009 Sebastian Benz.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Sebastian Benz - initial API and implementation
 *      Michael Vorburger - extensions for EFactoryXtextIntegrationTest UC etc.
 ******************************************************************************/
package com.googlecode.efactory.scoping;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;
import org.eclipse.xtext.scoping.impl.AbstractDeclarativeScopeProvider;
import org.eclipse.xtext.scoping.impl.SimpleScope;

import com.google.inject.Inject;
import com.googlecode.efactory.eFactory.Attribute;
import com.googlecode.efactory.eFactory.CustomNameMapping;
import com.googlecode.efactory.eFactory.EnumAttribute;
import com.googlecode.efactory.eFactory.Factory;
import com.googlecode.efactory.eFactory.Feature;
import com.googlecode.efactory.eFactory.MultiValue;
import com.googlecode.efactory.eFactory.NewObject;
import com.googlecode.efactory.eFactory.PackageImport;
import com.googlecode.efactory.eFactory.Reference;
import com.googlecode.efactory.util.EcoreUtil3;

public class EFactoryScopeProvider extends AbstractDeclarativeScopeProvider {

	@Inject
	private EReferenceScopeProvider eReferenceScopeProvider;

	@Inject
	private IEPackageScopeProvider ePackageScopeProvider;

	public IScope scope_PackageImport_ePackage(PackageImport packageImport, EReference eReference) {
		final IScope parent = delegateGetScope(packageImport, eReference);
		return ePackageScopeProvider.createEPackageScope(packageImport.eResource(), parent);
	}
	
	public IScope scope_NewObject_eClass(Factory factory, EReference eReference) {
		final IScope parent = delegateGetScope(factory, eReference);
		return ePackageScopeProvider.createEClassScope(factory.eResource(), parent);
	}

	public IScope scope_EnumAttribute_value(EnumAttribute attribute, EReference reference) {
		Feature feature = getFeature(attribute);
		if (feature.getEFeature().getEType() instanceof EEnum) {
			EEnum enumType = (EEnum) feature.getEFeature().getEType();
			Iterable<IEObjectDescription> elements = Scopes.scopedElementsFor(enumType.getELiterals());
			return new SimpleScope(elements);
		}
		return IScope.NULLSCOPE;
	}

	// Feature == Containment here, always, is it?
	public IScope scope_NewObject_eClass(Feature feature, EReference eReference) {
		if (feature.getEFeature() instanceof EReference) {
			final IScope parent = delegateGetScope(feature, eReference);
			return ePackageScopeProvider.createEClassScope(feature.eResource(), (EClass) feature.getEFeature().getEType(), parent);
		} else
			return IScope.NULLSCOPE;
			
	}

	// This may look a bit strange, but is required for 
	// com.googlecode.efactory.ui.contentassist.EFactoryProposalProvider.completeFeature_EFeature()
	public IScope scope_Feature_eFeature(NewObject newObject, EReference reference) {
		EClass eClass = newObject.getEClass();
		Iterable<? extends EObject> assignableFeature = EcoreUtil3.getAssignableFeatures(eClass);
		return new SimpleScope(Scopes.scopedElementsFor(assignableFeature));
	}
	public IScope scope_Feature_eFeature(Feature feature, EReference reference) {
		NewObject newObject = (NewObject) feature.eContainer();
		return scope_Feature_eFeature(newObject, reference);
	}

	public IScope scope_Feature_reference(NewObject newObject, EReference reference) {
		if (newObject.getEClass() == null) {
			return IScope.NULLSCOPE;
		}
		return new SimpleScope(Scopes.scopedElementsFor(newObject.getEClass().getEAllStructuralFeatures()));
	}

	public IScope scope_NewObject_eClass(Reference reference, EReference eReference) {
		return IScope.NULLSCOPE;
	}

	public IScope scope_Reference_value(Feature feature, EReference eReference) {
		EStructuralFeature sourceFeature = feature.getEFeature();
		if (EcoreUtil3.isEReference(sourceFeature)) {
			EReference realEReference = (EReference) sourceFeature;
			EObject context = feature.eContainer(); // This isn't correct of course.. it will be the NewObject instead of the real EObject created to mirror it... but as that may not be available yet, and this works, it's good enough.
			IScope parentScope = delegateGetScope(context, realEReference);
			// TODO double check if there are duplicates now? Then filter them here..
			return eReferenceScopeProvider.get(parentScope, feature.eResource(), (EClass) sourceFeature.getEType());
		}
		return IScope.NULLSCOPE;
	}

	public IScope scope_CustomNameMapping_nameFeature(
			CustomNameMapping mapping, EReference reference) {
		Iterable<EAttribute> attributes = EcoreUtil3.getAllAttributes(
				mapping.getEClass(), String.class);
		Iterable<IEObjectDescription> elements = Scopes
				.scopedElementsFor(attributes);
		return new SimpleScope(elements);
	}

	public IScope scope_CustomNameMapping_eClass(EObject context, EReference eReference) {
		final IScope parent = delegateGetScope(context, eReference);
		return ePackageScopeProvider.createEClassScope(context.eResource(), parent);
	}

	public IScope scope_Containment_value(Feature feature, EReference eReference) {
		if (EcoreUtil3.isEContainment(feature.getEFeature())) {
			return super.getDelegate().getScope(feature, eReference);
		}
		return IScope.NULLSCOPE;
	}

	protected Feature getFeature(Attribute attribute) {
		 EObject container = attribute.eContainer();
		 if (container instanceof MultiValue) {
			 // MultiValue mv = (MultiValue) container;
			 container = container.eContainer();
		 }
		 return (Feature) container; 
	}
}
