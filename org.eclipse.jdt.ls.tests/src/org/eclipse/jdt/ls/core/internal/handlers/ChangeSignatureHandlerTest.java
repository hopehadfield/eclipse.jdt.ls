/*******************************************************************************
 * Copyright (c) 2023 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.refactoring.ParameterInfo;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.LanguageServerWorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.handlers.ChangeSignatureHandler.MethodException;
import org.eclipse.jdt.ls.core.internal.handlers.ChangeSignatureHandler.MethodParameter;
import org.eclipse.jdt.ls.core.internal.text.correction.ChangeSignatureInfo;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactorProposalUtility;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ChangeSignatureHandlerTest extends AbstractCompilationUnitBasedTest {

	@Mock
	private JavaClientConnection connection;
	private IJavaProject fJavaProject;
	private IPackageFragmentRoot fRoot;
	private IPackageFragment fPackageP;

	@Override
	@Before
	public void setup() throws Exception {
		fJavaProject = newEmptyProject();
		fRoot = fJavaProject.findPackageFragmentRoot(fJavaProject.getPath().append("src"));
		assertNotNull(fRoot);
		fPackageP = fRoot.createPackageFragment("p", true, null);
		wcOwner = new LanguageServerWorkingCopyOwner(connection);
		server = new JDTLanguageServer(projectsManager, this.preferenceManager);
	}

	@Test
	public void testChangeSignatureRefactoringExists() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	public void getName(String input) {\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "getName");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> changeSignatureAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.REFACTOR_CHANGE_SIGNATURE);
		Assert.assertNotNull(changeSignatureAction);
		Command changeSignatureCommand = CodeActionHandlerTest.getCommand(changeSignatureAction);
		Assert.assertNotNull(changeSignatureCommand);
		Assert.assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, changeSignatureCommand.getCommand());
		List<Object> arguments = changeSignatureCommand.getArguments();
		Assert.assertEquals(2, arguments.size());
		Object arg0 = arguments.get(0);
		assertEquals(true, arg0 instanceof String);
		assertEquals("changeSignature", arg0);
		Object arg1 = arguments.get(1);
		assertEquals(true, arg1 instanceof CodeActionParams);
		ChangeSignatureInfo info = server.getChangeSignatureInfo((CodeActionParams) arg1).join();
		assertEquals("public", info.modifier);
		assertEquals(0, info.exceptions.length);
		assertEquals("=TestProject/src<p{A.java[A~getName~QString;", info.methodIdentifier);
		assertEquals("getName", info.methodName);
		assertEquals(1, info.parameters.length);
		assertEquals("", info.parameters[0].defaultValue);
		assertEquals("input", info.parameters[0].name);
		assertEquals(0, info.parameters[0].originalIndex);
		assertEquals("String", info.parameters[0].type);
		assertEquals("void", info.returnType);
	}

	@Test
	public void testChangeSignatureRefactoring() throws CoreException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	public void getName(String input) {\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "getName");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> changeSignatureAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.REFACTOR_CHANGE_SIGNATURE);
		Assert.assertNotNull(changeSignatureAction);
		Command changeSignatureCommand = CodeActionHandlerTest.getCommand(changeSignatureAction);
		Assert.assertNotNull(changeSignatureCommand);
		Assert.assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, changeSignatureCommand.getCommand());
		List<Object> arguments = changeSignatureCommand.getArguments();
		Assert.assertEquals(2, arguments.size());
		Object arg1 = arguments.get(1);
		assertEquals(true, arg1 instanceof CodeActionParams);
		ChangeSignatureInfo info = server.getChangeSignatureInfo((CodeActionParams) arg1).join();
		IJavaElement element = JavaCore.create(info.methodIdentifier);
		assertEquals(true, element instanceof IMethod);
		List<MethodParameter> parameters = List.of(info.parameters[0], new MethodParameter("String", "input1", "null", ParameterInfo.INDEX_FOR_ADDED));
		List<MethodException> exceptions = List.of(new MethodException("IOException", null));
		Refactoring refactoring = ChangeSignatureHandler.getChangeSignatureRefactoring((CodeActionParams) arg1, (IMethod) element, false, "getName1", JdtFlags.VISIBILITY_STRING_PRIVATE, "String", parameters, exceptions);
		Change change = refactoring.createChange(new NullProgressMonitor());
		change.perform(new NullProgressMonitor());
		//@formatter:off
		String expected = "package p;\r\n" +
						"\r\n" +
						"import java.io.IOException;\r\n" +
						"\r\n" +
						"public class A {\r\n" +
						"	private String getName1(String input, String input1) throws IOException {\r\n" +
						"	}\r\n" +
						"}";
		//@formatter:on
		assertEquals(expected, unit.getSource());
	}

	@Test
	public void testChangeSignatureRefactoringJavadocAddDelete() throws CoreException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("A.java", "package p;\r\n" +
				"\r\n" +
				"import java.io.IOException;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	/**\r\n" +
				"	 * @param input TODO\r\n" +
				"	 * @throws IOException TODO\r\n" +
				"	 *\r\n" +
				"	 */\r\n" +
				"	public void getName(String input) throws IOException {\r\n" +
				"	}\r\n" +
				"}"
				, true, null);
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "getName");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		Either<Command, CodeAction> changeSignatureAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.REFACTOR_CHANGE_SIGNATURE);
		Assert.assertNotNull(changeSignatureAction);
		Command changeSignatureCommand = CodeActionHandlerTest.getCommand(changeSignatureAction);
		Assert.assertNotNull(changeSignatureCommand);
		Assert.assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, changeSignatureCommand.getCommand());
		List<Object> arguments = changeSignatureCommand.getArguments();
		Assert.assertEquals(2, arguments.size());
		Object arg1 = arguments.get(1);
		assertEquals(true, arg1 instanceof CodeActionParams);
		ChangeSignatureInfo info = server.getChangeSignatureInfo((CodeActionParams) arg1).join();
		IJavaElement element = JavaCore.create(info.methodIdentifier);
		assertEquals(true, element instanceof IMethod);
		List<MethodParameter> parameters = List.of(info.parameters[0], new MethodParameter("String", "input1", "null", ParameterInfo.INDEX_FOR_ADDED));
		List<MethodException> exceptions = List.of(info.exceptions[0], new MethodException("Exception", null));
		Refactoring refactoring = ChangeSignatureHandler.getChangeSignatureRefactoring((CodeActionParams) arg1, (IMethod) element, false, "getName1", JdtFlags.VISIBILITY_STRING_PRIVATE, "String", parameters, exceptions);
		Change change = refactoring.createChange(new NullProgressMonitor());
		change.perform(new NullProgressMonitor());
		//@formatter:off
		String expected = "package p;\r\n" +
						"\r\n" +
						"import java.io.IOException;\r\n" +
						"\r\n" +
						"public class A {\r\n" +
						"	/**\r\n" +
						"	 * @param input TODO\r\n" +
						"	 * @param input1 TODO\r\n" +
						"	 * @return TODO\r\n" +
						"	 * @throws IOException TODO\r\n" +
						"	 * @throws Exception TODO\r\n" +
						"	 *\r\n" +
						"	 */\r\n" +
						"	private String getName1(String input, String input1) throws IOException, Exception {\r\n" +
						"	}\r\n" +
						"}";
		//@formatter:on
		assertEquals(expected, unit.getSource());

		ICompilationUnit unitTwo = fPackageP.createCompilationUnit("A.java", expected, true, null);
		params = CodeActionUtil.constructCodeActionParams(unit, "getName");
		codeActions = server.codeAction(params).join();
		Assert.assertNotNull(codeActions);
		changeSignatureAction = CodeActionHandlerTest.findAction(codeActions, JavaCodeActionKind.REFACTOR_CHANGE_SIGNATURE);
		Assert.assertNotNull(changeSignatureAction);
		changeSignatureCommand = CodeActionHandlerTest.getCommand(changeSignatureAction);
		Assert.assertNotNull(changeSignatureCommand);
		Assert.assertEquals(RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID, changeSignatureCommand.getCommand());
		arguments = changeSignatureCommand.getArguments();
		Assert.assertEquals(2, arguments.size());
		arg1 = arguments.get(1);
		assertEquals(true, arg1 instanceof CodeActionParams);
		info = server.getChangeSignatureInfo((CodeActionParams) arg1).join();
		element = JavaCore.create(info.methodIdentifier);
		assertEquals(true, element instanceof IMethod);
		parameters = List.of(info.parameters[1]);
		exceptions = List.of(info.exceptions[1]);
		refactoring = ChangeSignatureHandler.getChangeSignatureRefactoring((CodeActionParams) arg1, (IMethod) element, false, "getName1", JdtFlags.VISIBILITY_STRING_PRIVATE, "String", parameters, exceptions);
		change = refactoring.createChange(new NullProgressMonitor());
		change.perform(new NullProgressMonitor());
		//@formatter:off
				String expectedTwo = "package p;\r\n" +
								"\r\n" +
								"public class A {\r\n" +
								"	/**\r\n" +
								"	 * @param input1 TODO\r\n" +
								"	 * @return TODO\r\n" +
								"	 * @throws Exception TODO\r\n" +
								"	 *\r\n" +
								"	 */\r\n" +
								"	private String getName1(String input1) throws Exception {\r\n" +
								"	}\r\n" +
								"}";
				//@formatter:on
		assertEquals(expectedTwo, unitTwo.getSource());
	}
}
