
import java.lang.Math;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.awt.Container;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.BoxLayout;
import javax.media.opengl.GL;
import javax.media.opengl.GLCanvas;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLAutoDrawable;
// import javax.media.opengl.GLDrawableFactory;
import javax.media.opengl.GLEventListener;

import com.sun.opengl.util.GLUT;


class ColoredBox {
	public static final float DEFAULT_SIZE = 0.5f;
	public static final float DEFAULT_ALPHA = 0.5f;

	public AlignedBox3D box;

	// The color and alpha components, each in [0,1]
	public float r=1, g=1, b=1, a=1;

	public boolean isSelected = false;

	public ColoredBox(
		AlignedBox3D alignedBox3D,
		float red, float green, float blue, float alpha
	) {
		box = new AlignedBox3D(
			alignedBox3D.getMin(),
			alignedBox3D.getMax()
		);
		r = red;
		g = green;
		b = blue;
		a = alpha;
	}

}


class Scene {
	public Vector< ColoredBox > coloredBoxes = new Vector< ColoredBox >();

	AlignedBox3D boundingBoxOfScene = new AlignedBox3D();
	boolean isBoundingBoxOfSceneDirty = false;
	boolean drawWireframeBoxes = false;




	public Scene() {
	}

	public AlignedBox3D getBoundingBoxOfScene() {
		if ( isBoundingBoxOfSceneDirty ) {
			boundingBoxOfScene.clear();
			for ( int i = 0; i < coloredBoxes.size(); ++i ) {
				boundingBoxOfScene.bound( coloredBoxes.elementAt(i).box );
			}
			isBoundingBoxOfSceneDirty = false;
		}
		return boundingBoxOfScene;
	}

	public void addColoredBox(
		AlignedBox3D box,
		float red, float green, float blue,
		float alpha
	) {
		ColoredBox cb = new ColoredBox( box, red, green, blue, alpha );
		coloredBoxes.addElement( cb );
		isBoundingBoxOfSceneDirty = true;
	}

	public int getIndexOfIntersectedBox(
		Ray3D ray, // input
		Point3D intersectionPoint, // output
		Vector3D normalAtIntersection // output
	) {
		boolean intersectionDetected = false;
		int indexOfIntersectedBox = -1;
		float distanceToIntersection = 0;

		// candidate intersection
		Point3D candidatePoint = new Point3D();
		Vector3D candidateNormal = new Vector3D();
		float candidateDistance;

		for ( int i = 0; i < coloredBoxes.size(); ++i ) {
			AlignedBox3D box = coloredBoxes.elementAt(i).box;
			if (box.intersects(ray,candidatePoint,candidateNormal)) {
				candidateDistance = Point3D.diff(
					ray.origin, candidatePoint
				).length();
				if (
					! intersectionDetected
					|| candidateDistance < distanceToIntersection
				) {
					// We've found a new, best candidate
					intersectionDetected = true;
					indexOfIntersectedBox = i;
					distanceToIntersection = candidateDistance;
					intersectionPoint.copy( candidatePoint );
					normalAtIntersection.copy( candidateNormal );
				}
			}
		}
		return indexOfIntersectedBox;
	}

	public AlignedBox3D getBox( int index ) {
		if ( 0 <= index && index < coloredBoxes.size() )
			return coloredBoxes.elementAt(index).box;
		return null;
	}

	public boolean getSelectionStateOfBox( int index ) {
		if ( 0 <= index && index < coloredBoxes.size() )
			return coloredBoxes.elementAt(index).isSelected;
		return false;
	}
	public void setSelectionStateOfBox( int index, boolean state ) {
		if ( 0 <= index && index < coloredBoxes.size() )
			coloredBoxes.elementAt(index).isSelected = state;
	}
	public void toggleSelectionStateOfBox( int index ) {
		if ( 0 <= index && index < coloredBoxes.size() ) {
			ColoredBox cb = coloredBoxes.elementAt(index);
			cb.isSelected = ! cb.isSelected;
		}
	}
	
	public void setDrawWireframeBoxes(boolean drawWireframeBoxes){
		this.drawWireframeBoxes = drawWireframeBoxes;
	}

	public void setColorOfBox( int index, float r, float g, float b ) {
		if ( 0 <= index && index < coloredBoxes.size() ) {
			ColoredBox cb = coloredBoxes.elementAt(index);
			cb.r = r;
			cb.g = g;
			cb.b = b;
		}
	}

	public void translateBox( int index, Vector3D translation ) {
		if ( 0 <= index && index < coloredBoxes.size() ) {
			ColoredBox cb = coloredBoxes.elementAt(index);
			AlignedBox3D oldBox = cb.box;
			cb.box = new AlignedBox3D(
				Point3D.sum( oldBox.getMin(), translation ),
				Point3D.sum( oldBox.getMax(), translation )
			);
			isBoundingBoxOfSceneDirty = true;
		}
	}

	public void resizeBox(
		int indexOfBox, int indexOfCornerToResize, Vector3D translation
	) {
		if ( 0 <= indexOfBox && indexOfBox < coloredBoxes.size() ) {
			ColoredBox cb = coloredBoxes.elementAt(indexOfBox);
			AlignedBox3D oldBox = cb.box;
			cb.box = new AlignedBox3D();

			// One corner of the new box will be the corner of the old
			// box that is diagonally opposite the corner being resized ...
			cb.box.bound( oldBox.getCorner( indexOfCornerToResize ^ 7 ) );

			// ... and the other corner of the new box will be the
			// corner being resized, after translation.
			cb.box.bound( Point3D.sum( oldBox.getCorner( indexOfCornerToResize ), translation ) );

			isBoundingBoxOfSceneDirty = true;
		}
	}

	public void deleteBox( int index ) {
		if ( 0 <= index && index < coloredBoxes.size() ) {
			coloredBoxes.removeElementAt( index );
			isBoundingBoxOfSceneDirty = true;
		}
	}


	public void deleteAllBoxes() {
		coloredBoxes.removeAllElements();
		isBoundingBoxOfSceneDirty = true;
	}


	static public void drawBox(
		GL gl,
		AlignedBox3D box,
		boolean expand,
		boolean drawAsWireframe,
		boolean cornersOnly
	) {
		if ( expand ) {
			float diagonal = box.getDiagonal().length();
			diagonal /= 20;
			Vector3D v = new Vector3D( diagonal, diagonal, diagonal );
			box = new AlignedBox3D( Point3D.diff(box.getMin(),v), Point3D.sum(box.getMax(),v) );
		}
		if ( drawAsWireframe ) {
			if ( cornersOnly ) {
				gl.glBegin( GL.GL_LINES );
				for ( int dim = 0; dim < 3; ++dim ) {
					Vector3D v = Vector3D.mult( Point3D.diff(box.getCorner(1<<dim),box.getCorner(0)), 0.1f );
					for ( int a = 0; a < 2; ++a ) {
						for ( int b = 0; b < 2; ++b ) {
							int i = (a << ((dim+1)%3)) | (b << ((dim+2)%3));
							gl.glVertex3fv( box.getCorner(i).get(), 0 );
							gl.glVertex3fv( Point3D.sum( box.getCorner(i), v ).get(), 0 );
							i |= 1 << dim;
							gl.glVertex3fv( box.getCorner(i).get(), 0 );
							gl.glVertex3fv( Point3D.diff( box.getCorner(i), v ).get(), 0 );
						}
					}
				}
				gl.glEnd();
			}
			else {
				gl.glBegin( GL.GL_LINE_STRIP );
					gl.glVertex3fv( box.getCorner( 0 ).get(), 0 );
					gl.glVertex3fv( box.getCorner( 1 ).get(), 0 );
					gl.glVertex3fv( box.getCorner( 3 ).get(), 0 );
					gl.glVertex3fv( box.getCorner( 2 ).get(), 0 );
					gl.glVertex3fv( box.getCorner( 6 ).get(), 0 );
					gl.glVertex3fv( box.getCorner( 7 ).get(), 0 );
					gl.glVertex3fv( box.getCorner( 5 ).get(), 0 );
					gl.glVertex3fv( box.getCorner( 4 ).get(), 0 );
					gl.glVertex3fv( box.getCorner( 0 ).get(), 0 );
					gl.glVertex3fv( box.getCorner( 2 ).get(), 0 );
				gl.glEnd();
				gl.glBegin( GL.GL_LINES );
					gl.glVertex3fv( box.getCorner( 1 ).get(), 0 );
					gl.glVertex3fv( box.getCorner( 5 ).get(), 0 );
					gl.glVertex3fv( box.getCorner( 3 ).get(), 0 );
					gl.glVertex3fv( box.getCorner( 7 ).get(), 0 );
					gl.glVertex3fv( box.getCorner( 4 ).get(), 0 );
					gl.glVertex3fv( box.getCorner( 6 ).get(), 0 );
				gl.glEnd();
			}
		}
		else {
			gl.glBegin( GL.GL_QUAD_STRIP );
				gl.glVertex3fv( box.getCorner( 0 ).get(), 0 );
				gl.glVertex3fv( box.getCorner( 1 ).get(), 0 );
				gl.glVertex3fv( box.getCorner( 4 ).get(), 0 );
				gl.glVertex3fv( box.getCorner( 5 ).get(), 0 );
				gl.glVertex3fv( box.getCorner( 6 ).get(), 0 );
				gl.glVertex3fv( box.getCorner( 7 ).get(), 0 );
				gl.glVertex3fv( box.getCorner( 2 ).get(), 0 );
				gl.glVertex3fv( box.getCorner( 3 ).get(), 0 );
				gl.glVertex3fv( box.getCorner( 0 ).get(), 0 );
				gl.glVertex3fv( box.getCorner( 1 ).get(), 0 );
			gl.glEnd();

			gl.glBegin( GL.GL_QUADS );
				gl.glVertex3fv( box.getCorner( 1 ).get(), 0 );
				gl.glVertex3fv( box.getCorner( 3 ).get(), 0 );
				gl.glVertex3fv( box.getCorner( 7 ).get(), 0 );
				gl.glVertex3fv( box.getCorner( 5 ).get(), 0 );

				gl.glVertex3fv( box.getCorner( 0 ).get(), 0 );
				gl.glVertex3fv( box.getCorner( 4 ).get(), 0 );
				gl.glVertex3fv( box.getCorner( 6 ).get(), 0 );
				gl.glVertex3fv( box.getCorner( 2 ).get(), 0 );
			gl.glEnd();
		}
	}


	public void drawScene(
		GL gl,
		int indexOfHilitedBox, // -1 for none
		boolean useAlphaBlending
	) {
		if ( useAlphaBlending ) {
			gl.glDisable(GL.GL_DEPTH_TEST);
			gl.glDepthMask(false);
			gl.glBlendFunc( GL.GL_SRC_ALPHA, GL.GL_ONE );
			gl.glEnable( GL.GL_BLEND );
		}
		for ( int i = 0; i < coloredBoxes.size(); ++i ) {
			ColoredBox cb = coloredBoxes.elementAt(i);
			if ( useAlphaBlending )
				gl.glColor4f( cb.r, cb.g, cb.b, cb.a );
			else
				gl.glColor3f( cb.r, cb.g, cb.b );
			drawBox( gl, cb.box, false, drawWireframeBoxes, false );
			
		}
		if ( useAlphaBlending ) {
			gl.glDisable( GL.GL_BLEND );
			gl.glDepthMask(true);
			gl.glEnable(GL.GL_DEPTH_TEST);
		}
		for ( int i = 0; i < coloredBoxes.size(); ++i ) {
			ColoredBox cb = coloredBoxes.elementAt(i);
			if ( cb.isSelected && indexOfHilitedBox == i )
				gl.glColor3f( 1, 1, 0 );
			else if ( cb.isSelected )
				gl.glColor3f( 1, 0, 0 );
			else if ( indexOfHilitedBox == i )
				gl.glColor3f( 0, 1, 0 );
			else continue;
			drawBox( gl, cb.box, true, true, true );
		}
	}

	public void drawBoundingBoxOfScene( GL gl ) {
		AlignedBox3D box = getBoundingBoxOfScene();
		if ( ! box.isEmpty() )
			drawBox( gl, box, false, true, false );
	}
}




class SceneViewer extends GLCanvas implements MouseListener, MouseMotionListener, GLEventListener {

	GLUT glut;

	public Scene scene = new Scene();
	public int indexOfSelectedBox = -1; // -1 for none
	public List<Integer> listIndexOfSelectedBoxes = new ArrayList<Integer>();
	private Point3D selectedPoint = new Point3D();
	private Vector3D normalAtSelectedPoint = new Vector3D();
	public int indexOfHilitedBox = -1; // -1 for none
	private Point3D hilitedPoint = new Point3D();
	private Vector3D normalAtHilitedPoint = new Vector3D();

	Camera3D camera = new Camera3D();

	RadialMenuWidget radialMenu = new RadialMenuWidget();
	private static final int COMMAND_CREATE_BOX = 0;
	private static final int COMMAND_COLOR_RED = 1;
	private static final int COMMAND_COLOR_YELLOW = 2;
	private static final int COMMAND_COLOR_GREEN = 3;
	private static final int COMMAND_COLOR_BLUE = 4;
	private static final int COMMAND_DELETE = 5;

	public boolean displayWorldAxes = false;
	public boolean displayCameraTarget = false;
	public boolean displayBoundingBox = false;
	public boolean enableCompositing = false;
	public boolean drawWireframeBoxesCheckBox = false;

	int mouse_x, mouse_y, old_mouse_x, old_mouse_y;

	public SceneViewer( GLCapabilities caps ) {

		super( caps );
		addGLEventListener(this);

		addMouseListener( this );
		addMouseMotionListener( this );

		radialMenu.setItemLabelAndID( RadialMenuWidget.CENTRAL_ITEM, "", COMMAND_CREATE_BOX );
		radialMenu.setItemLabelAndID( 1, "Create Box", COMMAND_CREATE_BOX );
		radialMenu.setItemLabelAndID( 2, "Set Color to Red", COMMAND_COLOR_RED );
		radialMenu.setItemLabelAndID( 3, "Set Color to Yellow", COMMAND_COLOR_YELLOW );
		radialMenu.setItemLabelAndID( 4, "Set Color to Green", COMMAND_COLOR_GREEN );
		radialMenu.setItemLabelAndID( 5, "Set Color to Blue", COMMAND_COLOR_BLUE );
		radialMenu.setItemLabelAndID( 7, "Delete Box", COMMAND_DELETE );

		camera.setSceneRadius( (float)Math.max(
			5 * ColoredBox.DEFAULT_SIZE,
			scene.getBoundingBoxOfScene().getDiagonal().length() * 0.5f
		) );
		camera.reset();

	}
	public Dimension getPreferredSize() {
		return new Dimension( 512, 512 );
	}

	float clamp( float x, float min, float max ) {
		if ( x < min ) return min;
		if ( x > max ) return max;
		return x;
	}
	public void createNewBox() {
		Vector3D halfDiagonalOfNewBox = new Vector3D(
			ColoredBox.DEFAULT_SIZE*0.5f,
			ColoredBox.DEFAULT_SIZE*0.5f,
			ColoredBox.DEFAULT_SIZE*0.5f
		);
		if ( indexOfSelectedBox >= 0 ) {
			ColoredBox cb = scene.coloredBoxes.elementAt(indexOfSelectedBox);
			Point3D centerOfNewBox = Point3D.sum(
				Point3D.sum(
					cb.box.getCenter(),
					Vector3D.mult(
						normalAtSelectedPoint,
						0.5f*(float)Math.abs(Vector3D.dot(cb.box.getDiagonal(),normalAtSelectedPoint))
					)
				),
				Vector3D.mult( normalAtSelectedPoint, ColoredBox.DEFAULT_SIZE*0.5f )
			);
			scene.addColoredBox(
				new AlignedBox3D(
					Point3D.diff( centerOfNewBox, halfDiagonalOfNewBox ),
					Point3D.sum( centerOfNewBox, halfDiagonalOfNewBox )
				),
				clamp( cb.r + 0.5f*((float)Math.random()-0.5f), 0, 1 ),
				clamp( cb.g + 0.5f*((float)Math.random()-0.5f), 0, 1 ),
				clamp( cb.b + 0.5f*((float)Math.random()-0.5f), 0, 1 ),
				cb.a
			);
		}
		else {
			Point3D centerOfNewBox = camera.target;
			scene.addColoredBox(
				new AlignedBox3D(
					Point3D.diff( centerOfNewBox, halfDiagonalOfNewBox ),
					Point3D.sum( centerOfNewBox, halfDiagonalOfNewBox )
				),
				(float)Math.random(),
				(float)Math.random(),
				(float)Math.random(),
				ColoredBox.DEFAULT_ALPHA
			);
			normalAtSelectedPoint = new Vector3D(1,0,0);
		}

		// de-select the old boxes
		for(int index : listIndexOfSelectedBoxes){
			scene.setSelectionStateOfBox( index, false );
		}
		listIndexOfSelectedBoxes.clear();
		
		indexOfSelectedBox = scene.coloredBoxes.size() - 1;
		scene.setSelectionStateOfBox( indexOfSelectedBox, true );
		if(!listIndexOfSelectedBoxes.contains(indexOfSelectedBox)){
			listIndexOfSelectedBoxes.add(indexOfSelectedBox);
		}
	}

	public void setColorOfSelection( float r, float g, float b ) {
		if ( indexOfSelectedBox >= 0 ) {
			scene.setColorOfBox( indexOfSelectedBox, r, g, b );
		}
	}

	public void deleteSelection() {
		if ( listIndexOfSelectedBoxes.size() > 0 ) {
			Collections.sort(listIndexOfSelectedBoxes);
			Collections.reverse(listIndexOfSelectedBoxes);
			for(int index : listIndexOfSelectedBoxes){
				scene.deleteBox( index );
			}
			listIndexOfSelectedBoxes.clear();
			indexOfSelectedBox = -1;
			indexOfHilitedBox = -1;
		}
	}

	public void deleteAll() {
		scene.deleteAllBoxes();
		indexOfSelectedBox = -1;
		indexOfHilitedBox = -1;
	}

	public void lookAtSelection() {
		if ( indexOfSelectedBox >= 0 ) {
			Point3D p = scene.getBox( indexOfSelectedBox ).getCenter();
			camera.lookAt(p);
		}
	}

	public void resetCamera() {
		camera.setSceneRadius( (float)Math.max(
			5 * ColoredBox.DEFAULT_SIZE,
			scene.getBoundingBoxOfScene().getDiagonal().length() * 0.5f
		) );
		camera.reset();
	}
	
	public void setDrawWireframeBoxes(boolean drawWireframeBoxes){
		scene.setDrawWireframeBoxes(drawWireframeBoxes);
	}

	public void init( GLAutoDrawable drawable ) {
		GL gl = drawable.getGL();
		gl.glClearColor( 0, 0, 0, 0 );
		glut = new GLUT();
	}
	public void reshape(
		GLAutoDrawable drawable,
		int x, int y, int width, int height
	) {
		GL gl = drawable.getGL();
		camera.setViewportDimensions(width, height);

		// set viewport
		gl.glViewport(0, 0, width, height);
	}
	public void displayChanged(
		GLAutoDrawable drawable,
		boolean modeChanged,
		boolean deviceChanged
	) {
		// leave this empty
	}
	public void display( GLAutoDrawable drawable ) {
		GL gl = drawable.getGL();
		gl.glMatrixMode( GL.GL_PROJECTION );
		gl.glLoadIdentity();
		camera.transform( gl );
		gl.glMatrixMode( GL.GL_MODELVIEW );
		gl.glLoadIdentity();

		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

		gl.glDepthFunc( GL.GL_LEQUAL );
		gl.glEnable( GL.GL_DEPTH_TEST );
		gl.glEnable( GL.GL_CULL_FACE );
		gl.glFrontFace(GL.GL_CCW);
		gl.glDisable( GL.GL_LIGHTING );
		gl.glShadeModel( GL.GL_FLAT );

		scene.drawScene( gl, indexOfHilitedBox, enableCompositing );

		//create a line for showing the direction of the selected face of the selected box
		if ( indexOfSelectedBox >= 0 ) {
			gl.glBegin( GL.GL_LINES );
			//white color
			gl.glColor3f( 1, 1, 1 );
			//first point is the center of the selected box
			gl.glVertex3f(scene.coloredBoxes.elementAt(indexOfSelectedBox).box.getCenter().x(),scene.coloredBoxes.elementAt(indexOfSelectedBox).box.getCenter().y(),scene.coloredBoxes.elementAt(indexOfSelectedBox).box.getCenter().z());
			//second point is the center of the box + the normal at the selected point
			gl.glVertex3f(scene.coloredBoxes.elementAt(indexOfSelectedBox).box.getCenter().x()+normalAtSelectedPoint.v[0],scene.coloredBoxes.elementAt(indexOfSelectedBox).box.getCenter().y()+normalAtSelectedPoint.v[1],scene.coloredBoxes.elementAt(indexOfSelectedBox).box.getCenter().z()+normalAtSelectedPoint.v[2]);
			gl.glEnd();
		}
		if ( displayWorldAxes ) {
			gl.glBegin( GL.GL_LINES );
				gl.glColor3f( 1, 0, 0 );
				gl.glVertex3f(0,0,0);
				gl.glVertex3f(1,0,0);
				gl.glColor3f( 0, 1, 0 );
				gl.glVertex3f(0,0,0);
				gl.glVertex3f(0,1,0);
				gl.glColor3f( 0, 0, 1 );
				gl.glVertex3f(0,0,0);
				gl.glVertex3f(0,0,1);
			gl.glEnd();
		}
		if ( displayCameraTarget ) {
			gl.glBegin( GL.GL_LINES );
				gl.glColor3f( 1, 1, 1 );
				gl.glVertex3fv( Point3D.sum( camera.target, new Vector3D(-0.5f,    0,    0) ).get(), 0 );
				gl.glVertex3fv( Point3D.sum( camera.target, new Vector3D( 0.5f,    0,    0) ).get(), 0 );
				gl.glVertex3fv( Point3D.sum( camera.target, new Vector3D(    0,-0.5f,    0) ).get(), 0 );
				gl.glVertex3fv( Point3D.sum( camera.target, new Vector3D(    0, 0.5f,    0) ).get(), 0 );
				gl.glVertex3fv( Point3D.sum( camera.target, new Vector3D(    0,    0,-0.5f) ).get(), 0 );
				gl.glVertex3fv( Point3D.sum( camera.target, new Vector3D(    0,    0, 0.5f) ).get(), 0 );
			gl.glEnd();
		}
		if ( displayBoundingBox ) {
			gl.glColor3f( 0.5f, 0.5f, 0.5f );
			scene.drawBoundingBoxOfScene( gl );
		}

		if ( radialMenu.isVisible() ) {
			radialMenu.draw( gl, glut, getWidth(), getHeight() );
		}

		// gl.glFlush(); // I don't think this is necessary
	}

	private void updateHiliting() {
		Ray3D ray = camera.computeRay(mouse_x,mouse_y);
		Point3D newIntersectionPoint = new Point3D();
		Vector3D newNormalAtIntersection = new Vector3D();
		int newIndexOfHilitedBox = scene.getIndexOfIntersectedBox(
			ray, newIntersectionPoint, newNormalAtIntersection
		);
		hilitedPoint.copy( newIntersectionPoint );
		normalAtHilitedPoint.copy( newNormalAtIntersection );
		if ( newIndexOfHilitedBox != indexOfHilitedBox ) {
			indexOfHilitedBox = newIndexOfHilitedBox;
			repaint();
		}
	}

	public void mouseClicked( MouseEvent e ) { }
	public void mouseEntered( MouseEvent e ) { }
	public void mouseExited( MouseEvent e ) { }

	public void mousePressed( MouseEvent e ) {
		old_mouse_x = mouse_x;
		old_mouse_y = mouse_y;
		mouse_x = e.getX();
		mouse_y = e.getY();

		if ( radialMenu.isVisible() || (SwingUtilities.isRightMouseButton(e) && !e.isShiftDown() && !e.isControlDown()) ) {
			int returnValue = radialMenu.pressEvent( mouse_x, mouse_y );
			if ( returnValue == CustomWidget.S_REDRAW )
				repaint();
			if ( returnValue != CustomWidget.S_EVENT_NOT_CONSUMED )
				return;
		}

		updateHiliting();

		if ( SwingUtilities.isLeftMouseButton(e) && !e.isControlDown()) {
			if ( listIndexOfSelectedBoxes.size() > 0){
				if(hilitedPoint.x() == 0 && hilitedPoint.y() == 0 && hilitedPoint.z() == 0){
					// de-select the old boxes
					for(int index : listIndexOfSelectedBoxes){
						scene.setSelectionStateOfBox( index, false );
					}
					listIndexOfSelectedBoxes.clear();
				}
			}
			indexOfSelectedBox = indexOfHilitedBox;
			selectedPoint.copy( hilitedPoint );
			normalAtSelectedPoint.copy( normalAtHilitedPoint );
			if ( indexOfSelectedBox >= 0 ) {
				scene.setSelectionStateOfBox( indexOfSelectedBox, true );
				if(!listIndexOfSelectedBoxes.contains(indexOfSelectedBox)){
					listIndexOfSelectedBoxes.add(indexOfSelectedBox);
				}
				System.out.println(listIndexOfSelectedBoxes.size());
			}
			repaint();
		}
	}

	public void mouseReleased( MouseEvent e ) {
		old_mouse_x = mouse_x;
		old_mouse_y = mouse_y;
		mouse_x = e.getX();
		mouse_y = e.getY();

		if ( radialMenu.isVisible() ) {
			int returnValue = radialMenu.releaseEvent( mouse_x, mouse_y );

			int itemID = radialMenu.getItemID(radialMenu.getSelection());
			switch ( itemID ) {
			case COMMAND_CREATE_BOX :
				createNewBox();
				break;
			case COMMAND_COLOR_RED :
				setColorOfSelection( 1, 0, 0 );
				break;
			case COMMAND_COLOR_YELLOW :
				setColorOfSelection( 1, 1, 0 );
				break;
			case COMMAND_COLOR_GREEN :
				setColorOfSelection( 0, 1, 0 );
				break;
			case COMMAND_COLOR_BLUE :
				setColorOfSelection( 0, 0, 1 );
				break;
			case COMMAND_DELETE :
				deleteSelection();
				break;
			}

			repaint();

			if ( returnValue != CustomWidget.S_EVENT_NOT_CONSUMED )
				return;
		}
	}

	public void mouseMoved( MouseEvent e ) {

		old_mouse_x = mouse_x;
		old_mouse_y = mouse_y;
		mouse_x = e.getX();
		mouse_y = e.getY();

		if ( radialMenu.isVisible() ) {
			int returnValue = radialMenu.moveEvent( mouse_x, mouse_y );
			if ( returnValue == CustomWidget.S_REDRAW )
				repaint();
			if ( returnValue != CustomWidget.S_EVENT_NOT_CONSUMED )
				return;
		}

		updateHiliting();
	}

	public void mouseDragged( MouseEvent e ) {
		old_mouse_x = mouse_x;
		old_mouse_y = mouse_y;
		mouse_x = e.getX();
		mouse_y = e.getY();
		int delta_x = mouse_x - old_mouse_x;
		int delta_y = old_mouse_y - mouse_y;
		if ( radialMenu.isVisible() ) {
			int returnValue = radialMenu.dragEvent( mouse_x, mouse_y );
			if ( returnValue == CustomWidget.S_REDRAW )
				repaint();
			if ( returnValue != CustomWidget.S_EVENT_NOT_CONSUMED )
				return;
		}
		else if (e.isControlDown()) {
			if (
				SwingUtilities.isLeftMouseButton(e)
				&& SwingUtilities.isRightMouseButton(e)
			) {
				camera.dollyCameraForward(
					(float)(3*(delta_x+delta_y)), false
				);
			}
			else if ( SwingUtilities.isLeftMouseButton(e) ) {
				camera.orbit(old_mouse_x,old_mouse_y,mouse_x,mouse_y);
			}
			else {
				camera.translateSceneRightAndUp(
					(float)(delta_x), (float)(delta_y)
				);
			}
			repaint();
		}
		else if (
			SwingUtilities.isLeftMouseButton(e) && !e.isControlDown()
			&& indexOfSelectedBox >= 0
		) {
			if ( !e.isShiftDown() ) {
				// translate a box

				Ray3D ray1 = camera.computeRay( old_mouse_x, old_mouse_y );
				Ray3D ray2 = camera.computeRay( mouse_x, mouse_y );
				Point3D intersection1 = new Point3D();
				Point3D intersection2 = new Point3D();
				Plane plane = new Plane( normalAtSelectedPoint, selectedPoint );
				if (
					plane.intersects( ray1, intersection1, true )
					&& plane.intersects( ray2, intersection2, true )
				) {
					Vector3D translation = Point3D.diff( intersection2, intersection1 );
					for(int index : listIndexOfSelectedBoxes){
						scene.translateBox( index, translation );
					}
					repaint();
				}
			}
			else {
				// resize a box

				Ray3D ray1 = camera.computeRay( old_mouse_x, old_mouse_y );
				Ray3D ray2 = camera.computeRay( mouse_x, mouse_y );
				Point3D intersection1 = new Point3D();
				Point3D intersection2 = new Point3D();
				Vector3D v1 = Vector3D.cross( normalAtSelectedPoint, ray1.direction );
				Vector3D v2 = Vector3D.cross( normalAtSelectedPoint, v1 );
				Plane plane = new Plane( v2, selectedPoint );
				if (
					plane.intersects( ray1, intersection1, true )
					&& plane.intersects( ray2, intersection2, true )
				) {
					Vector3D translation = Point3D.diff( intersection2, intersection1 );

					// project the translation onto the normal, so that it is only along one axis
					translation = Vector3D.mult( normalAtSelectedPoint, Vector3D.dot( normalAtSelectedPoint, translation ) );
					scene.resizeBox(
						indexOfSelectedBox,
						scene.coloredBoxes.elementAt(indexOfSelectedBox).box.getIndexOfExtremeCorner(normalAtSelectedPoint),
						translation
					);
					repaint();
				}
			}
		}
	}
}

public class SimpleModeller implements ActionListener {

	static final String applicationName = "Simple Modeller";

	JFrame frame;
	Container toolPanel;
	SceneViewer sceneViewer;

	JMenuItem deleteAllMenuItem, quitMenuItem, aboutMenuItem;
	JButton createBoxButton;
	JButton deleteSelectionButton;
	JButton lookAtSelectionButton;
	JButton resetCameraButton;
	JCheckBox displayWorldAxesCheckBox;
	JCheckBox displayCameraTargetCheckBox;
	JCheckBox displayBoundingBoxCheckBox;
	JCheckBox enableCompositingCheckBox;
	JCheckBox drawWireframeBoxesCheckBox;
	

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if ( source == deleteAllMenuItem ) {
			int response = JOptionPane.showConfirmDialog(
				frame,
				"Really delete all?",
				"Confirm Delete All",
				JOptionPane.YES_NO_OPTION
			);

			if (response == JOptionPane.YES_OPTION) {
				sceneViewer.deleteAll();
				sceneViewer.repaint();
			}
		}
		else if ( source == quitMenuItem ) {
			int response = JOptionPane.showConfirmDialog(
				frame,
				"Really quit?",
				"Confirm Quit",
				JOptionPane.YES_NO_OPTION
			);

			if (response == JOptionPane.YES_OPTION) {
				System.exit(0);
			}
		}
		else if ( source == aboutMenuItem ) {
			JOptionPane.showMessageDialog(
				frame,
				"'" + applicationName + "' Sample Program\n"
					+ "Original version written April-May 2008",
				"About",
				JOptionPane.INFORMATION_MESSAGE
			);
		}
		else if ( source == createBoxButton ) {
			sceneViewer.createNewBox();
			sceneViewer.repaint();
		}
		else if ( source == deleteSelectionButton ) {
			sceneViewer.deleteSelection();
			sceneViewer.repaint();
		}
		else if ( source == lookAtSelectionButton ) {
			sceneViewer.lookAtSelection();
			sceneViewer.repaint();
		}
		else if ( source == resetCameraButton ) {
			sceneViewer.resetCamera();
			sceneViewer.repaint();
		}
		else if ( source == displayWorldAxesCheckBox ) {
			sceneViewer.displayWorldAxes = ! sceneViewer.displayWorldAxes;
			sceneViewer.repaint();
		}
		else if ( source == displayCameraTargetCheckBox ) {
			sceneViewer.displayCameraTarget = ! sceneViewer.displayCameraTarget;
			sceneViewer.repaint();
		}
		else if ( source == displayBoundingBoxCheckBox ) {
			sceneViewer.displayBoundingBox = ! sceneViewer.displayBoundingBox;
			sceneViewer.repaint();
		}
		else if ( source == enableCompositingCheckBox ) {
			sceneViewer.enableCompositing = ! sceneViewer.enableCompositing;
			sceneViewer.repaint();
		}
		else if ( source == drawWireframeBoxesCheckBox ) {
//			sceneViewer.drawWireframeBoxesCheckBox = ! sceneViewer.drawWireframeBoxesCheckBox;
			sceneViewer.setDrawWireframeBoxes(drawWireframeBoxesCheckBox.isSelected());
			sceneViewer.repaint();
		}
		
		
	}


	// For thread safety, this should be invoked
	// from the event-dispatching thread.
	//
	private void createUI() {
		if ( ! SwingUtilities.isEventDispatchThread() ) {
			System.out.println(
				"Warning: UI is not being created in the Event Dispatch Thread!");
			assert false;
		}

		frame = new JFrame( applicationName );
		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

		JMenuBar menuBar = new JMenuBar();
			JMenu menu = new JMenu("File");
				deleteAllMenuItem = new JMenuItem("Delete All");
				deleteAllMenuItem.addActionListener(this);
				menu.add(deleteAllMenuItem);

				menu.addSeparator();

				quitMenuItem = new JMenuItem("Quit");
				quitMenuItem.addActionListener(this);
				menu.add(quitMenuItem);
			menuBar.add(menu);
			menu = new JMenu("Help");
				aboutMenuItem = new JMenuItem("About");
				aboutMenuItem.addActionListener(this);
				menu.add(aboutMenuItem);
			menuBar.add(menu);
		frame.setJMenuBar(menuBar);

		toolPanel = new JPanel();
		toolPanel.setLayout( new BoxLayout( toolPanel, BoxLayout.Y_AXIS ) );

		// Need to set visible first before starting the rendering thread due
		// to a bug in JOGL. See JOGL Issue #54 for more information on this
		// https://jogl.dev.java.net/issues/show_bug.cgi?id=54
		frame.setVisible(true);

		GLCapabilities caps = new GLCapabilities();
		caps.setDoubleBuffered(true);
		caps.setHardwareAccelerated(true);
		sceneViewer = new SceneViewer(caps);

		Container pane = frame.getContentPane();
		// We used to use a BoxLayout as the layout manager here,
		// but it caused problems with resizing behavior due to
		// a JOGL bug https://jogl.dev.java.net/issues/show_bug.cgi?id=135
		pane.setLayout( new BorderLayout() );
		pane.add( toolPanel, BorderLayout.LINE_START );
		pane.add( sceneViewer, BorderLayout.CENTER );

		createBoxButton = new JButton("Create Box");
		createBoxButton.setAlignmentX( Component.LEFT_ALIGNMENT );
		createBoxButton.addActionListener(this);
		toolPanel.add( createBoxButton );

		deleteSelectionButton = new JButton("Delete Selection");
		deleteSelectionButton.setAlignmentX( Component.LEFT_ALIGNMENT );
		deleteSelectionButton.addActionListener(this);
		toolPanel.add( deleteSelectionButton );

		lookAtSelectionButton = new JButton("Look At Selection");
		lookAtSelectionButton.setAlignmentX( Component.LEFT_ALIGNMENT );
		lookAtSelectionButton.addActionListener(this);
		toolPanel.add( lookAtSelectionButton );

		resetCameraButton = new JButton("Reset Camera");
		resetCameraButton.setAlignmentX( Component.LEFT_ALIGNMENT );
		resetCameraButton.addActionListener(this);
		toolPanel.add( resetCameraButton );

		displayWorldAxesCheckBox = new JCheckBox("Display World Axes", sceneViewer.displayWorldAxes );
		displayWorldAxesCheckBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		displayWorldAxesCheckBox.addActionListener(this);
		toolPanel.add( displayWorldAxesCheckBox );

		displayCameraTargetCheckBox = new JCheckBox("Display Camera Target", sceneViewer.displayCameraTarget );
		displayCameraTargetCheckBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		displayCameraTargetCheckBox.addActionListener(this);
		toolPanel.add( displayCameraTargetCheckBox );

		displayBoundingBoxCheckBox = new JCheckBox("Display Bounding Box", sceneViewer.displayBoundingBox );
		displayBoundingBoxCheckBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		displayBoundingBoxCheckBox.addActionListener(this);
		toolPanel.add( displayBoundingBoxCheckBox );

		enableCompositingCheckBox = new JCheckBox("Enable Compositing", sceneViewer.enableCompositing );
		enableCompositingCheckBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		enableCompositingCheckBox.addActionListener(this);
		toolPanel.add( enableCompositingCheckBox );
		
		drawWireframeBoxesCheckBox = new JCheckBox("Draw Wireframe Boxes", sceneViewer.enableCompositing );
		drawWireframeBoxesCheckBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		drawWireframeBoxesCheckBox.addActionListener(this);
		toolPanel.add( drawWireframeBoxesCheckBox );

		frame.pack();
		frame.setVisible( true );
	}

	public static void main( String[] args ) {
		// Schedule the creation of the UI for the event-dispatching thread.
		javax.swing.SwingUtilities.invokeLater(
			new Runnable() {
				public void run() {
					SimpleModeller sp = new SimpleModeller();
					sp.createUI();
				}
			}
		);
	}
}

