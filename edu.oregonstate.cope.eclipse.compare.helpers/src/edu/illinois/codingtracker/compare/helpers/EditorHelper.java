/**
 * This file is licensed under the University of Illinois/NCSA Open Source License. See LICENSE.TXT for details.
 */
package edu.illinois.codingtracker.compare.helpers;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.ResourceNode;
import org.eclipse.compare.internal.CompareEditor;
import org.eclipse.compare.structuremergeviewer.ICompareInput;
import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.UIPlugin;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;

import edu.illinois.codingtracker.helpers.ResourceHelper;

/**
 * 
 * @author Stas Negara
 * @author Mohsen Vakilian - Extracted this class from CodeChangeTracker
 * 
 */
@SuppressWarnings("restriction")
public class EditorHelper {

	private static final int MAXIMUM_OPEN_EDITORS_COUNT = 50; // Does not limit
																// the number of
																// CompareEditors.

	private static final List<ITextEditor> existingEditors = new LinkedList<ITextEditor>(); // Does
																							// not
																							// include
																							// CompareEditors.

	public static IFile getEditedJavaFile(CompareEditor compareEditor) {
		IFile javaFile = null;
		IEditorInput editorInput = compareEditor.getEditorInput();
		if (editorInput instanceof CompareEditorInput) {
			CompareEditorInput compareEditorInput = (CompareEditorInput) editorInput;
			Object compareResult = compareEditorInput.getCompareResult();
			if (compareResult instanceof ICompareInput) {
				ICompareInput compareInput = (ICompareInput) compareResult;
				ITypedElement leftTypedElement = compareInput.getLeft();
				if (leftTypedElement instanceof ResourceNode) {
					ResourceNode resourceNode = (ResourceNode) leftTypedElement;
					IResource resource = resourceNode.getResource();
					if (resource instanceof IFile) {
						IFile file = (IFile) resource;
						if (ResourceHelper.isJavaFile(file)) {
							javaFile = file;
						}
					}
				}
			}
		}
		return javaFile;
	}

	public static IFile getEditedJavaFile(AbstractDecoratedTextEditor editor) {
		IFile javaFile = null;
		IEditorInput editorInput = editor.getEditorInput();
		if (editorInput instanceof FileEditorInput) {
			IFile file = ((FileEditorInput) editorInput).getFile();
			if (ResourceHelper.isJavaFile(file)) {
				javaFile = file;
			}
		}
		return javaFile;
	}

	/*
	 * MH: commented to remove hacked dependency and make the code compile
	 */
	// public static ISourceViewer getEditingSourceViewer(CompareEditor
	// compareEditor) {
	// ISourceViewer sourceViewer= null;
	// IEditorInput editorInput= compareEditor.getEditorInput();
	// if (editorInput instanceof CompareEditorInput) {
	// CompareEditorInput compareEditorInput= (CompareEditorInput)editorInput;
	// Viewer contentViewer= compareEditorInput.getContentViewer();
	// if (contentViewer instanceof TextMergeViewer) {
	// sourceViewer= ((TextMergeViewer)contentViewer).getLeftViewer();
	// }
	// }
	// return sourceViewer;
	// }
	//
	// public static ISourceViewer
	// getEditingSourceViewer(AbstractDecoratedTextEditor editor) {
	// return editor.getHackedViewer();
	// }

	public static IEditorPart getActiveEditor() {
		return JavaPlugin.getActivePage().getActiveEditor();
	}

	public static IDocument getEditedDocument(ITextEditor editor) {
		return editor.getDocumentProvider().getDocument(editor.getEditorInput());
	}

	public static boolean isExistingEditor(IEditorPart editorPart) {
		return existingEditors.contains(editorPart);
	}

	public static ITextEditor openEditor(String filePath) throws CoreException {
		ITextEditor fileEditor = getExistingEditor(filePath);
		if (fileEditor != null) {
			activateEditor(fileEditor);
		} else {
			fileEditor = createEditor(filePath);
		}
		return fileEditor;
	}

	public static void closeAllEditors() {
		JavaPlugin.getActivePage().closeAllEditors(false);
		existingEditors.clear();
		// existingCompareEditors.clear();
	}

	public static void closeEditorSynchronously(IEditorPart editorPart) {
		// This closes the given editor synchronously.
		boolean success = editorPart.getSite().getPage().closeEditor(editorPart, false);
		if (!success) {
			throw new RuntimeException("Could not close editor: " + editorPart);
		}
		existingEditors.remove(editorPart);
	}

	/**
	 * Has a side effect of bringing to top the newly created editor.
	 * 
	 * @return
	 * @throws JavaModelException
	 * @throws PartInitException
	 */
	public static ITextEditor createEditor(String filePath) throws JavaModelException, PartInitException {
		IFile file = (IFile) ResourceHelper.findWorkspaceMember(filePath);
		ITextEditor newTextEditor = (ITextEditor) JavaUI.openInEditor(JavaCore.createCompilationUnitFrom(file));
		addNewEditorToExistingEditors(newTextEditor);
		return newTextEditor;
	}

	public static Set<ITextEditor> getExistingEditors(String resourcePath) throws PartInitException {
		Set<ITextEditor> existingResourceEditors = new HashSet<ITextEditor>();
		for (ITextEditor textEditor : existingEditors) {
			IEditorInput editorInput = textEditor.getEditorInput();
			if (editorInput instanceof FileEditorInput && (ResourceHelper.getPortableResourcePath(((FileEditorInput) editorInput).getFile()).startsWith(resourcePath))) {
				existingResourceEditors.add(textEditor);
			}
		}
		return existingResourceEditors;
	}

	// MH REPLACE WITH GO FIND IT FUNCTIONALITY INSTEAD OF KEEPING A LIST
//	public static ITextEditor getExistingEditor(String resourcePath) throws PartInitException {
//		Set<ITextEditor> existingEditors = getExistingEditors(resourcePath);
//		if (!existingEditors.isEmpty()) {
//			return existingEditors.iterator().next();
//		}
//		return null;
//	}

	public static void closeAllEditorsForResource(String resourcePath) throws PartInitException {
		for (ITextEditor resourceEditor : getExistingEditors(resourcePath)) {
			closeEditorSynchronously(resourceEditor);
		}
	}

	private static void addNewEditorToExistingEditors(ITextEditor textEditor) {
		if (existingEditors.contains(textEditor)) {
			throw new RuntimeException("The new editor is already in the existing editors list: " + textEditor);
		}
		// Add the new editor to the front of the existing editors list as the
		// most recent new editor.
		existingEditors.add(0, textEditor);

		// Ensure the size of the existing editors list does not exceed the
		// maximum allowed size.
		int existingEditorsCount = existingEditors.size();
		if (existingEditorsCount > MAXIMUM_OPEN_EDITORS_COUNT) {
			// Close the oldest not dirty editor.
			for (int i = existingEditorsCount - 1; i >= 0; i--) {
				ITextEditor existingEditor = existingEditors.get(i);
				if (!existingEditor.isDirty()) {
					closeEditorSynchronously(existingEditor);
					return;
				}
			}
		}
	}

	public static void activateEditor(IEditorPart editor) {
		JavaPlugin.getActivePage().activate(editor);
//		if (!(editor instanceof CompareEditor)) {
//			// Move the activated editor to the front of the existing editors
//			// list as the most recent activated editor.
//			boolean isExistingEditor = existingEditors.remove(editor);
//			if (!isExistingEditor) {
//				throw new RuntimeException("Trying to activate an editor that is not part of the existing editors list: " + editor);
//			}
//			existingEditors.add(0, (ITextEditor) editor);
//		}
	}

	public static ITextEditor getExistingEditor(String resourcePath) throws PartInitException {
		 ITextEditor editor = getExistingEditorForResource(resourcePath);
		 if (editor == null)
			 editor = getNewEditorForResource(resourcePath);
		 
		 activateEditor(editor);
		 return editor;
	}

	private static ITextEditor getNewEditorForResource(String resourcePath) {
		// TODO Auto-generated method stub
		return null;
	}

	private static ITextEditor getExistingEditorForResource(String resourcePath) throws PartInitException {
		IWorkbenchWindow activeWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IEditorReference[] editorReferences = activeWindow.getActivePage().getEditorReferences();
		for (IEditorReference editorReference : editorReferences) {
			String fileLocation = ((FileEditorInput)editorReference.getEditorInput()).getFile().getFullPath().toString();
			//ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
			//IDocument document = getDocumentForEditor(editorReference);
			//if (document == null)
			//	continue;

			//ITextFileBuffer textFileBuffer = bufferManager.getTextFileBuffer(document);
			//String fileLocation = textFileBuffer.getLocation().toPortableString();
			//String fileLocation = editorReference.
			if (fileLocation.equals(resourcePath)) {
				//BringToFront
				return  (ITextEditor) editorReference.getEditor(true);
			}
		}
		// open editor
		//return openIEditor(fileName);
		return null;
	}
	
 	public static IDocument getDocumentForEditor(String fileName) {
		IWorkbenchWindow activeWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IEditorReference[] editorReferences = activeWindow.getActivePage().getEditorReferences();
		for (IEditorReference editorReference : editorReferences) {
			ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
			IDocument document = getDocumentForEditor(editorReference);
			if (document == null)
				continue;

			ITextFileBuffer textFileBuffer = bufferManager.getTextFileBuffer(document);
			String fileLocation = textFileBuffer.getLocation().toPortableString();
			if (fileLocation.equals(fileName)) {
				editorReference.getEditor(true).setFocus(); // might need to be
															// done in UI thread
				return document;
			}
		}
		// open editor
		return openIEditor(fileName);
	}

	private static IDocument openIEditor(String fileName) {
		IWorkbenchPage page = UIPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(fileName));
		IEditorDescriptor desc = PlatformUI.getWorkbench().getEditorRegistry().getDefaultEditor(file.getName());
		try {
			IEditorPart openedEditor = page.openEditor(new FileEditorInput(file), desc.getId());
			return getDocumentForEditor(openedEditor);
		} catch (PartInitException e) {
		}
		return null;
	}

	private static IDocument getDocumentForEditor(IEditorReference editorReference) {
		IEditorPart editorPart = editorReference.getEditor(true);
		return getDocumentForEditor(editorPart);
	}

	private static IDocument getDocumentForEditor(IEditorPart editorPart) {
		if (editorPart instanceof MultiPageEditorPart) {
			// ((MultiPageEditorPart) editorPart).addPageChangedListener(new
			// MultiEditorPageChangedListener());
			return null;
		}
		ISourceViewer sourceViewer = (ISourceViewer) editorPart.getAdapter(ITextOperationTarget.class);
		IDocument document = sourceViewer.getDocument();
		return document;
	}

}
