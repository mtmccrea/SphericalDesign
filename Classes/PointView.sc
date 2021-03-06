PointViewCtl : View {
	var pv; // PointView

	var autoChks, invChks;
	var rotSls;
	var rotRadNbs, rotDegNbs, rotPerNbs;
	// show hide: axes, indices, connections
	var axChk, idxChk, conChk;
	var headerTxts;
	var mstrLayout;

	*new { |pointView, bounds = (Rect(0,0, 400, 250))|
		^super.new(pointView, bounds).init(pointView);
	}

	init { |pointView|
		pv = pointView;
		mstrLayout = HLayout();
		this.layout_(mstrLayout);
		// this.resize_(5);
		this.background_(Color.red.alpha_(0.25));

		// init controls
		autoChks = [\autoRotate, \autoTilt, \autoTumble].collect{ |method|
			CheckBox()
			.action_({ |ui|
				pv.perform((method ++ \_).asSymbol, ui.value)
			})
			.value_(pv.perform(method))
			;
		};

		// invert rotation directions of auto-rotate
		invChks = [\rotateDir, \tiltDir, \tumbleDir].collect{ |method|
			CheckBox()
			.action_({ |ui|
				pv.perform((method ++ \_).asSymbol, ui.value.asBoolean.if({-1},{1}))
			})
			.value_(pv.perform(method).asBoolean.not)
			;
		};

		// rotate sliders
		rotSls = [\rotate, \tilt, \tumble].collect{ |method|
			Slider()
			.action_({ |ui|
				pv.perform((method ++ \_).asSymbol, ui.value.linlin(0, 1, pi, -pi))
			})
			.value_(pv.perform(method).linlin(pi, -pi, 0, 1))
			.orientation_(\horizontal)
			.maxHeight_(45)
			;
		};

		// radian rotation NumberBoxes
		rotRadNbs = [\rotate, \tilt, \tumble].collect{ |method|
			NumberBox()
			.action_({ |ui|
				pv.perform((method ++ \_).asSymbol, ui.value * pi)
			})
			.clipLo_(-2).clipHi_(2)
			.step_(0.02).scroll_step_(0.02)
			.decimals_(2)
			.maxWidth_("-2.00".bounds.width * 1.3)
			.value_(pv.perform(method) / pi)
			.align_(\center)
			;
		};

		// degree rotation NumberBoxes
		rotDegNbs = [\rotate, \tilt, \tumble].collect{ |method|
			NumberBox()
			.action_({ |ui|
				pv.perform((method ++ \_).asSymbol, ui.value.degrad)
			})
			.clipLo_(-360).clipHi_(360)
			.step_(0.5).scroll_step_(0.5)
			.decimals_(1)
			.maxWidth_("-180.0".bounds.width * 1.3)
			.value_(pv.perform(method).degrad)
			.align_(\center)
			;
		};

		// rotation period NumberBoxes
		rotPerNbs = [\rotatePeriod, \tiltPeriod, \tumblePeriod].collect{ |method|
			NumberBox()
			.action_({ |ui|
				pv.perform((method ++ \_).asSymbol, ui.value)
			})
			.clipLo_(0.01).clipHi_(1000)
			.step_(0.5).scroll_step_(0.5)
			.decimals_(1)
			.maxWidth_("500.5".bounds.width * 1.3)
			.value_(pv.perform(method))
			.align_(\center)
			;
		};

		// labels above control columns
		headerTxts = ["Rotate/Tilt/Tumble", "pi", "deg", "auto", "T", "inv"].collect{|txt|
			StaticText()
			.string_(txt)
			.align_(\center)
			;
		};

		// rotSls.do(mstrLayout.add(_));
		// rotRadNbs.do(mstrLayout.add(_));
		// rotDegNbs.do(mstrLayout.add(_));
		// autoChks.do(mstrLayout.add(_));
		// rotPerNbs.do(mstrLayout.add(_));
		// invChks.do(mstrLayout.add(_));

		[rotSls, rotRadNbs, rotDegNbs, autoChks, rotPerNbs, invChks].do({ |ctls, i|
			var col;
			col = ctls.insert(0, headerTxts[i]).add(nil);
			mstrLayout.add(VLayout(*col))
		});
	}
}


PointView : View {
	var <points; // points should be Cartesians
	var <connections;
	var axisPnts;

	// drawing
	var <cen, <minDim;
	var skewX = 0, skewY = 0;
	var translateX = 0, translateY = 0;
	var az, bz = 3;              // perspective parameters, see originDist_, eyeDist_
	var showIndices = true;      // show indices of points
	var showAxes = true;         // show world axes
	var showConnections = false; // show connections between points
	var xyz, axisColors, axisScale = 1;
	var frameRate = 25;
	var <pointColors, prPntDrawCols;
	var colsByHue = true, huesScrambled = false;     // if the colors have been set by hue range
	var <pointSize = 15, <pointDistScale = 0.333;

	// movement
	var <rotate = 0, <tilt = 0, <tumble = 0; // radians
	var <rotateRate = 0.1, <tiltRate = 0.1, <tumbleRate = 0.1; // Hz
	var <rotateStep, tiltStep, tumbleStep; // radians
	var <rotateDir = 1, <tiltDir  = 1, <tumbleDir = 1; // +/-1
	var <autoRotate = false, <autoTumble = false, <autoTilt = false;

	// views
	var <userView, ctlView;

	// interaction
	var mouseDownPnt, mouseUpPnt, mouseMovePnt;

	*new { |parent, bounds = (Rect(0,0, 600, 500))|
		^super.new(parent, bounds).init;
	}

	init { |argSpec, initVal|

		points = [];
		az = bz + 1; // distance to point from eye

		userView = UserView(this, this.bounds.origin_(0@0))
		.resize_(5)
		.frameRate_(frameRate)
		.drawFunc_(this.drawFunc)
		;

		// origin, x, y, z
		axisPnts = [[0,0,0], [1,0,0], [0,1,0], [0,0,1]].collect(_.asCartesian);
		axisColors = [\blue, \red, \green].collect{|col| Color.perform(col, 1, 0.7) };
		xyz = #["X", "Y", "Z"];

		// init draw colors
		prPntDrawCols = [Color.hsv(0,1,1,1), Color.hsv(0.999,1,1,1)];
		colsByHue = true;

		// init rotation variables
		this.rotateRate_(rotateRate);
		this.tiltRate_(tiltRate);
		this.tumbleRate_(tumbleRate);

		// TODO:
		this.initInteractions;

		this.onResize_({ this.updateCanvasDims });
		// initialize canvas
		this.updateCanvasDims;

		// init controller view
		ctlView = PointViewCtl(this); //, Rect(5,5,200,this.bounds.height));
		this.addDependant(ctlView);
		ctlView.onClose({ this.removeDependant(ctlView) })
	}


	updateCanvasDims {
		var bnds;
		userView.bounds_(this.bounds.origin_(0@0));
		bnds = userView.bounds;
		cen  = bnds.center;
		minDim = min(bnds.width, bnds.height);
	}

	points_ { |cartesians|
		points = cartesians;
		connections = [(0..points.size-1)];
		this.prUpdateColors;
		this.refresh;
	}

	// Set points by directions.
	// Can be an Array of:
	// Sphericals, or
	// [[theta, phi], [theta, phi] ...], (rho assumed to be 1) or
	// [[theta, phi, rho], [theta, phi, rho] ...]
	directions_ { |dirArray|
		var first, sphericals;

		first = dirArray[0];
		sphericals = case
		{ first.isKindOf(Spherical) } {
			dirArray
		}
		{ first.size ==  2 } {
			dirArray.collect( Spherical(1, *_) )
		}
		{ first.size ==  3 } {
			dirArray.collect{ |tpr| tpr.postln; Spherical(tpr[2], tpr[0], tpr[1]) }
		}
		{
			"[PointView:-directions_] Invalid dirArray argument."
			"Can be an Array of: Sphericals, or [[theta, phi], [theta, phi] ...], "
			"(rho assumed to be 1), or [[theta, phi, rho], [theta, phi, rho] ...]"
			.throw
		};

		this.points_(sphericals.collect(_.asCartesian));
	}


	drawFunc {
		^{ |v|
			var scale;
			var pnts, pnts_xf, pnt_depths;
			var axPnts, axPnts_xf, axPnts_depths;
			var rotPnts, to2D, incStep;
			var strRect;
			var minPntSize;

			minPntSize = pointSize * pointDistScale;

			rotPnts = { |carts|
				carts.collect{ |pnt|
					pnt
					.rotate(0.5pi).tilt(0.5pi) // orient so view matches ambisonics
					.rotate(tilt).tilt(tumble).tumble(rotate) // user rotation
				};
			};

			// xformed points from 3D -> perspective -> 2D
			// + cart.z; accounts for depth adjusted by rotation
			// (az is the depth position of the _center_ of shape's rotation)
			// https://en.wikipedia.org/wiki/3D_projection
			to2D = { |carts|
				carts.collect{ |cart|
					(   cart
						+ (skewX @ skewY.neg)           // offset points within world, in normalized
						* (bz / (az + cart.z))          // add perspective
						+ (translateX @ translateY.neg) // translate the "world"
					).asPoint * scale                   // discard z for 2D drawing, scale to window size
				}
			};

			incStep = { |rotation, step| (rotation + step).wrap(-2pi, 2pi) };

			scale = minDim.half;

			if (autoRotate) { rotate = incStep.(rotate, rotateStep) };
			if (autoTilt) { tilt = incStep.(tilt, tiltStep) };
			if (autoTumble) { tumble = incStep.(tumble, tumbleStep) };

			// rotate into ambisonics coords and rotate for user
			pnts = rotPnts.(points);
			axPnts = rotPnts.(axisPnts * axisScale);

			// hold on to these point depths (z) for use when drawing with perspective
			// pnt_depths = sin(pnts.collect(_.z) * 0.5pi); // warp depth with a sin function, why not?
			pnt_depths = pnts.collect(_.z);
			axPnts_depths = axPnts.collect(_.z);

			// transform 3D positions to 2D points with perspective
			pnts_xf = to2D.(pnts);
			axPnts_xf = to2D.(axPnts);

			/* DRAW */

			// move to center
			Pen.translate(cen.x, cen.y);

			// draw axes
			if (showAxes) {
				var lineDpth, pntDepth, pntSize;
				var r, oxy, theta;

				strRect = "XX".bounds.asRect;
				r = strRect.width / 2;

				// axPnts_xf = [origin,x,y,z]
				axPnts_xf[1..].do{ |axPnt, i|
					pntDepth = axPnts_depths[i+1];

					// average the depth between pairs of connected points
					lineDpth = axPnts_depths[0] + pntDepth * 0.5;
					pntSize = pntDepth.linlin(-1.0,1.0, 15, 5);

					Pen.strokeColor_(axisColors[i]);
					Pen.moveTo(axPnts_xf[0]);
					Pen.width_(lineDpth.linlin(-1.0,1.0, 4, 0.5));
					Pen.lineTo(axPnt);
					Pen.stroke;

					// draw axis label
					theta = atan2(axPnt.y - axPnts_xf[0].y, axPnt.x - axPnts_xf[0].x);
					oxy = Point(theta.cos, theta.sin) * r;
					strRect = strRect.center_(axPnt + oxy);

					Pen.fillColor_(axisColors[i]);
					Pen.stringCenteredIn(
						xyz[i],
						strRect,
						Font.default.pointSize_(
							pntDepth.linlin(-1.0,1.0, 18, 10) // change font size with depth
						)
					);

				};
			};

			// draw points
			strRect = "000000".bounds.asRect;
			pnts_xf.do{ |pnt, i|
				var pntSize;

				pntSize = pnt_depths[i].linlin(-1.0,1.0, pointSize, pointSize * pointDistScale);

				// draw index
				if (showIndices) {
					Pen.fillColor_(Color.black);
					Pen.stringLeftJustIn(
						i.asString,
						strRect.left_(pnt.x + pntSize).bottom_(pnt.y + pntSize),
						Font.default.pointSize_(
							pnt_depths[i].linlin(-1.0,1.0, 18, 10) // change font size with depth
						)
					);
				};

				// draw point
				// Pen.fillColor_(Color.hsv(i / (pnts_xf.size - 1), 1, 1));
				Pen.fillColor_(prPntDrawCols.wrapAt(i));
				Pen.fillOval(Size(pntSize, pntSize).asRect.center_(pnt));
			};

			// draw connecting lines
			if (showConnections and: { connections.notNil }) {
				connections.do{ |set, i|
					var pDpths;

					// collect and average the depth between pairs of connected points
					pDpths = set.collect(pnt_depths[_]);
					pDpths = pDpths + pDpths.rotate(-1) / 2;

					Pen.strokeColor_(Color.blue.alpha_(0.1));
					Pen.moveTo(pnts_xf.at(set[0]));

					set.rotate(-1).do{ |idx, j|
						// change line width with depth
						Pen.width_(pDpths[j].linlin(-1.0,1.0, 4, 0.5));
						Pen.lineTo(pnts_xf[idx]);
						// stroke in the loop to retain independent
						// line widths within the set
						Pen.stroke;
						Pen.moveTo(pnts_xf[idx]);
					};
				};

			};
		}
	}


	/* Perspective controls */

	// skew/offset the points in the world (before perspective is added)
	skewX_ { |norm|
		skewX = norm;
		this.refresh;
		this.changed(\skewX, norm);
	}
	skewY_ { |norm|
		skewY = norm;
		this.refresh;
		this.changed(\skewY, norm);
	}

	// translate the world (after perspective is added)
	translateX_ { |norm|   // translateX: left -> right = -1 -> 1
		translateX = norm;
		this.refresh;
		this.changed(\translateX, norm);
	}
	translateY_ { |norm|   // translateY: bottom -> top = -1 -> 1
		translateY = norm;
		this.refresh;
		this.changed(\translateY, norm);
	}

	// distance of points' origin to screen
	originDist_ { |norm|
		az = bz + norm;
		this.refresh;
		this.changed(\originDist, norm);
	}
	// distance of eye to screen
	eyeDist_ { |norm|
		var temp = az - bz; // store origin offset
		bz = norm;
		az = bz + temp;
		this.refresh;
		this.changed(\eyeDist, norm);
	}

	pointSize_ { |px = 15|
		pointSize = px;
		this.refresh;
		this.changed(\pointSize, px);
	}

	pointDistScale_ { |norm = 0.333|
		pointDistScale = norm;
		this.refresh;
		this.changed(\pointDistScale, norm);
	}


	/* View movement controls */

	rotate_ { |radians|
		rotate = radians;
		autoRotate = false;
		this.refresh;
		this.changed(\rotate, radians);
	}
	tilt_ { |radians|
		tilt = radians;
		autoTilt = false;
		this.refresh;
		this.changed(\tilt, radians);
	}
	tumble_ { |radians|
		tumble = radians;
		autoTumble = false;
		this.refresh;
		this.changed(\tumble, radians);
	}

	// rotation direction: 1 ccw, -1 cw
	rotateDir_ { |dir|
		rotateDir = dir;
		rotateStep = (rotateRate / frameRate) * 2pi * rotateDir;
		this.changed(\rotateDir, rotateDir);
	}
	tiltDir_ { |dir|
		tiltDir = dir;
		tiltStep = (tiltRate / frameRate) * 2pi * tiltDir;
		this.changed(\tiltDir, tiltDir);
	}
	tumbleDir_ { |dir|
		tumbleDir = dir;
		tumbleStep = (tumbleRate / frameRate) * 2pi * tumbleDir;
		this.changed(\tumbleDir, tumbleDir);
	}

	rotateRate_ { |hz|
		rotateRate = hz;
		rotateStep = (rotateRate / frameRate) * 2pi * rotateDir;
		this.changed(\rotateRate, hz);
	}
	tiltRate_ { |hz|
		tiltRate = hz;
		tiltStep = (tiltRate / frameRate) * 2pi * tiltDir;
		this.changed(\tiltRate, hz);
	}
	tumbleRate_ { |hz|
		tumbleRate = hz;
		tumbleStep = (tumbleRate / frameRate) * 2pi * tumbleDir;
		this.changed(\tumbleRate, hz);
	}

	rotatePeriod_ { |s| this.rotateRate_(s.reciprocal) }
	tiltPeriod_ { |s| this.tiltRate_(s.reciprocal) }
	tumblePeriod_ { |s| this.tumbleRate_(s.reciprocal) }

	rotatePeriod { ^rotateRate.reciprocal }
	tiltPeriod { ^tiltRate.reciprocal }
	tumblePeriod { ^tumbleRate.reciprocal }

	autoRotate_ { |bool|
		autoRotate = bool;
		this.prCheckAnimate(\autoRotate, bool);
	}
	autoTilt_ { |bool|
		autoTilt = bool;
		this.prCheckAnimate(\autoTilt, bool);
	}
	autoTumble_ { |bool|
		autoTumble = bool;
		this.prCheckAnimate(\autoTumble, bool);
	}

	prCheckAnimate { |which, bool|
		userView.animate_(
			[autoRotate, autoTilt, autoTumble].any({|bool| bool});
		);
		this.changed(which, bool);
	}


	/* Display controls */

	showIndices_ { |bool|
		showIndices = bool;
		this.refresh;
	}

	showAxes_ { |bool|
		showAxes = bool;
		this.refresh;
	}

	showConnections_ { |bool|
		showConnections = bool;
		this.refresh;
	}

	// draw lines between these indices of points
	// e.g. [[1,3],[0,5],[2.4]]
	connections_ { |arraysOfIndices|
		if (arraysOfIndices.rank != 2) {
			"[PointView:-connections_] arraysOfIndices argument "
			"is not an array with rank == 2.".throw
		};

		connections = arraysOfIndices;
		showConnections = true;
		this.refresh;
	}

	axisColors_ { |colorArray|
		axisColors = colorArray;
		this.refresh;
	}

	axisScale_ { |scale|
		axisScale = scale;
		this.refresh;
	}

	frameRate_ { |hz|
		frameRate = hz;
		userView.frameRate_(hz);
		this.changed(\frameRate, hz);
	}


	/* Point color controls */

	// arrayOfColors can be a Color, Array of Colors.
	// If (arrayOfColors.size != points.size), points will wrap through the
	// color array, or be grouped into each color if colorSets has been set
	pointColors_ { |arrayOfColors|

		if (arrayOfColors.isKindOf(Color)) {
			arrayOfColors = [arrayOfColors];
		};

		if (
			arrayOfColors.isKindOf(Array) and:
			{ arrayOfColors.every({ |elem| elem.isKindOf(Color) }) }
		) {
			pointColors = arrayOfColors;
			prPntDrawCols = points.size.collect(pointColors.wrapAt(_));
			this.refresh;
		} {
			"[PointView:-pointColors_] arrayOfColors argument is not a Color or Array of Colors".throw;
		};
		colsByHue = false;
	}

	hueRange_ { |hueLow = 0, hueHigh = 0.999, sat = 0.9, val = 1, alpha = 0.8, scramble = false|
		var size = points.size;

		prPntDrawCols = size.collect{ |i|
			Color.hsv(
				(i / (size - 1)).linlin(0, 0.999, hueLow, hueHigh),
				sat, val, alpha
			)
		};
		if (scramble) {
			prPntDrawCols = prPntDrawCols.scramble;
			huesScrambled = scramble;
		};
		colsByHue = true;
	}

	// Set groups of point indices which belong to each color in
	// pointColors array.
	// defaultColor is a Color for points not includes in arraysOfIndices
	colorGroups_ { |arraysOfIndices, defaultColor = (Color.black)|

		prPntDrawCols = points.size.collect{defaultColor};

		if (arraysOfIndices.rank == 1) {
			arraysOfIndices = [arraysOfIndices];
		};

		arraysOfIndices.do{ |group, grpIdx|
			group.do{ |pntIdx|
				prPntDrawCols[pntIdx] = pointColors.wrapAt(grpIdx)
			}
		};
		colsByHue = false;
		this.refresh;
	}

	// called when points are set
	prUpdateColors {
		var hues, sat, val, alpha;
		if (colsByHue) {
			hues = prPntDrawCols.collect(_.hue);
			sat = prPntDrawCols.first.sat;
			val = prPntDrawCols.first.val;
			alpha = prPntDrawCols.first.alpha;
			this.hueRange_(hues.minItem, hues.maxItem, sat, val, alpha, huesScrambled);
		};

		prPntDrawCols ?? {
			prPntDrawCols = points.size.collect(pointColors.wrapAt(_));
			^this
		};

		if (prPntDrawCols.size != points.size) {

		}
	}


	// TODO:
	initInteractions {
		userView.mouseMoveAction_({
			|v,x,y,modifiers|
			mouseMovePnt = x@y;
			// mouseMoveAction.(v,x,y,modifiers)
		});

		userView.mouseDownAction_({
			|v,x,y, modifiers, buttonNumber, clickCount|
			mouseDownPnt = x@y;
			// mouseDownAction.(v,x,y, modifiers, buttonNumber, clickCount)
		});

		userView.mouseUpAction_({
			|v,x,y, modifiers|
			mouseUpPnt = x@y;
			// mouseUpAction.(v,x,y,modifiers)
		});

		userView.mouseWheelAction_({
			|v, x, y, modifiers, xDelta, yDelta|
			// this.stepByScroll(v, x, y, modifiers, xDelta, yDelta);
		});

		// NOTE: if overwriting this function, include a call to
		// this.stepByArrowKey(key) to retain key inc/decrement capability
		userView.keyDownAction_ ({
			|view, char, modifiers, unicode, keycode, key|
			// this.stepByArrowKey(key);
		});

	}

	refresh {
		userView.animate.not.if{ userView.refresh };
	}

}



/*

Usage

(
// p = TDesign(25).points;
p = [
	[0,0,1], [0,0,-1],
	[0,1,0], [0,-1,0],
	[1,0,0], [-1,0,0],
].collect(_.asCartesian);
v = PointView(bounds: [0,0, 400, 500].asRect).front.points_(p);
v.eyeDist = 2;
v.originDist = 2;
v.autoRotate = true;
v.showIndices = true;
v.showAxes = true;
)

v.skewX = 0.85;
v.skewY = 0.85;
v.eyeDist = 1.5;
v.originDist = 2.8;

v.eyeDist = 1;
v.originDist = 1;


(
d = [
	[ -18, 0 ], [ -54, 0 ], [ -90, 0 ], [ -126, 0 ], [ -162, 0 ], [ -198, 0 ], [ -234, 0 ], [ -270, 0 ], [ -306, 0 ], [ -342, 0 ],
	[ 0, -10 ], [ -72, -10 ], [ -144, -10 ], [ -216, -10 ], [ -288, -10 ],
	[ -45, 45 ], [ -135, 45 ], [ -225, 45 ], [ -315, 45 ], [ 0, 90 ]
];
a = VBAPSpeakerArray.new(3, d);
a.choose_ls_triplets;
p = d.degrad.collect({ |dir| Spherical(1, *dir).asCartesian});
// v = PointView(bounds: [0,0, 400, 500].asRect).front.points_(p);
v = PointView().front.points_(p);
v.eyeDist = 2;
v.originDist = 2;
v.autoRotate = true;
v.showIndices = true;
v.connections = a.sets.collect(_.chanOffsets.asInt);
v.translateY = 0.35;
v.skewY = -0.85;
v.frameRate = 135;
v.axisScale = 0.333;
)

// TODO:

// controls:
// - RTT controls
// - auto RTT with range/rate params
// - show axes
// - eye distance (perspective)
// features:
// - normalize input points (for drawing, not display)
// - orthogonal views: looking +/- XYZ axes (XY plane, XZ plane, etc.)
// - draw mesh in bundled points
// - specify point colors in groups or individually
// enhancements:
// - draw furthest points first so closer points drawn on top
// - break lines it segments to accentuate distance (axes especially)
// - show point info on mouseOver
// - add -highlightConnections(connection array) to emphasize a connection set (such as those triangles containing a VBAP source)
*/