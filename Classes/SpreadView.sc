SpreadView : ValuesView {
	var <innerRadiusRatio, <outerRadiusRatio, boarderPx;
	var <bnds, <cen, <maxRadius, <innerRadius, <outerRadius, <wedgeWidth; // set in drawFunc, for access by drawing layers
	var <handlePnts;
	var <>clickRangePx = 4;
	var <direction, <rangeCenterOffset;
	var <dirFlag; 				// cw=1, ccw=-1
	var <rangeStartAngle, <rangeSweepLength, <prRangeSweepLength, <prRangeStartAngle;

	// drawing layers. Add getters to get/set individual properties by '.p'
	var <range, <sprd, <handle, <curvalue, <label;
	/*
	spreadSpec: spec describing spread in radians, other specs will be inferred from this
	initVals: order of values in arrays - [value, center, spread, lo, hi], lo and hi can be nil to autofill from center/spread
	rangeCenterOffset: offset of "0" position from default (0 o'clock), degrees
	innerRadiusRatio=0: ratio of inner space
	outerRadiusRatio=1; ratio of outer edge of spread wedge
	*/

	*new {
		|parent, bounds, maxSpread, initVals, rangeCenterOffset=0, innerRadiusRatio=0, outerRadiusRatio=1|
		^super.new(parent, bounds, nil, initVals).init(rangeCenterOffset, innerRadiusRatio, outerRadiusRatio, maxSpread, initVals);
	}


	init {
		|argRangeCenterOffset, argInnerRadiusRatio, argOuterRadiusRatio, maxSpread, initVals|
		// REQUIRED: in subclass init, initialize drawing layers
		// initialize layer classes and save them to vars
		#range, sprd, handle, curvalue, label = [
			SprdRangeLayer, SprdSpreadLayer, SprdHandleLayer, SprdCurvalueLayer, SprdLabelLayer
		].collect({
			|class|
			class.new(this, class.properties)
		});
		// convenience variable to access a list of the layers
		layers = [range, sprd, handle, curvalue, label];

		rangeCenterOffset = argRangeCenterOffset;

		direction = \cw;
		dirFlag = 1;
		boarderPx = 1;

		this.initSpecsAndVals(maxSpread, initVals);

		this.rangeStartAngle = maxSpread.half.neg;		// reference 0 is UP
		this.rangeSweepLength = maxSpread;

		this.innerRadiusRatio_(argInnerRadiusRatio); // set innerRadiusRatio with setter to check sweepLength condition
		this.outerRadiusRatio_(argOuterRadiusRatio);

		// intialize pixel unit variables
		maxRadius = this.bounds.width/2;
		outerRadius = maxRadius*outerRadiusRatio;
		innerRadius = maxRadius*innerRadiusRatio;
		wedgeWidth = outerRadius-innerRadius;

		this.defineMouseActions;
		this.direction_(direction);  // this initializes prStarAngle and prSweepLength
	}

	// initialize [value, center, spread, lo, hi] specs and values
	initSpecsAndVals { |maxSpread, initVals|
		var lo, hi, valSpec, cenSpec, sprdSpec, loSpec, hiSpec, spcs;
		var v, c, s, l, h;
		var initError;

		// re-initialize specs
		lo = maxSpread.half.neg;
		hi = maxSpread.half;
		valSpec = [lo, hi].asSpec;
		cenSpec = [lo, hi].asSpec;
		sprdSpec = [0, maxSpread].asSpec;
		loSpec = [lo, hi].asSpec;
		hiSpec = [lo, hi].asSpec;
		// reset specs instance variable to be the array of specs
		spcs = [valSpec, cenSpec, sprdSpec, loSpec, hiSpec];

		spcs.do{|spec, i| this.specAt_(i, spec, false)};

		// re-initialize values
		// NOTE: center/spread vals take precedence over lo/hi
		#v, c, s, l, h = initVals;

		initError = {
			format(
				"Provide intial values for either center/spread or lo/hi. Provided: center % spread % lo % hi %\n",
				c, s, l, h
			).throw
		};

		if (c.isNil or: s.isNil) {
			if (l.isNil or: h.isNil) {initError.()} {
				// make sure both are specified
				if (l.notNil and: h.notNil) {initError.()};
				// define c, s by l, h
				c = l + (h-l).half;
				s = h-l;
			}
		} {
			// make sure both are specified
			if (c.notNil and: s.notNil) {
				l = c - s.half;
				h = c + s.half;
			} {initError.()};
		};

		// re-init values by spread and center
		this.curValue_(v, false);
		this.center_(c, false);
		this.spread_(s, false);
		this.lo_(l, false);
		this.hi_(h, true);
	}

	curValue_ { |deg, broadcast=true|
		this.valueAt_(0, deg, broadcast)
	}
	curValue { ^this.valueAt(0) }

	center_ { |deg, broadcast=true|
		this.valueAt_(1, deg, broadcast)
	}
	center { ^this.valueAt(1) }

	spread_ { |deg, broadcast=true|
		this.valueAt_(2, deg, broadcast)
	}
	spread { ^this.valueAt(2) }

	lo_ { |deg, broadcast=true|
		this.valueAt_(3, deg, broadcast)
	}
	lo { ^this.valueAt(3) }

	hi_ { |deg, broadcast=true|
		this.valueAt_(4, deg, broadcast)
	}
	hi { ^this.valueAt(4) }

	drawFunc {
		^{|v|
			// "global" instance vars, accessed by ValueViewLayers
			bnds = v.bounds;
			cen  = bnds.center;
			maxRadius = min(cen.x, cen.y) - boarderPx;
			outerRadius = maxRadius * outerRadiusRatio;
			innerRadius = maxRadius * innerRadiusRatio;
			wedgeWidth = outerRadius - innerRadius;

			this.calcHandlePnts;

			this.drawInThisOrder;
		};
	}

	calcHandlePnts {
		// lo, center, hi
		handlePnts = [inputs[3], inputs[1], inputs[4]].collect{ |rot|
			var theta, rho;
			theta = prRangeStartAngle + (rot * prRangeSweepLength);
			rho = outerRadius * handle.p.anchor;
			Polar(rho, theta).asPoint + cen;
		};
	}

	drawInThisOrder {
		if (range.p.show) {range.fill};
		if (sprd.p.show) {sprd.fill};
		if (handle.p.show) {
			if (handle.p.fill) {handle.fill};
			if (handle.p.stroke) {handle.stroke};
		};
	}

	defineMouseActions {
		var clicked, adjustLo, adjustCen, adjustHi;
		clicked = adjustLo = adjustCen = adjustHi = false;

		// assign action variables: down/move
		mouseDownAction = {
			|v, x, y|
			var dpnt;

			dpnt = x@y;

			block { |break|

				handlePnts.do{|hpnt, i|
					if (hpnt.dist(dpnt) < clickRangePx) {
						clicked = true;
						switch(i,
							0, {adjustLo = true},
							1, {adjustCen = true},
							2, {adjustHi = true},
						);
						break.()
					};
				}
			}
		};

		mouseMoveAction  = {
			|v, x, y|
			if (clicked) {
				this.respondToCircularMove(x@y)
			};
		};

		mouseUpAction = {
			|v, x, y|
			adjustLo = adjustCen = adjustHi = false;
			"lifted".postln;
		}
	}

	// radial change, relative to center
	respondToCircularMove {|mMovePnt|
		// var pos, rad, radRel;
		// pos = (mMovePnt - cen);
		// rad = atan2(pos.y,pos.x);					// radian position, relative 0 at 3 o'clock
		// radRel = rad + 0.5pi * dirFlag; 	// relative 0 at 12 o'clock, clockwise
		// radRel = (radRel - (startAngle*dirFlag)).wrap(0, 2pi); // relative to start position
		// if (radRel.inRange(0, sweepLength)) {
		// 	this.inputAction_(radRel/sweepLength); // triggers refresh
		// 	stValue = value;
		// 	stInput = input;
		// };
	}

	respondToAbsoluteClick {

		/* identify if near handles */

		// var pos, rad, radRel;
		// pos = (mouseDownPnt - cen);
		// rad = atan2(pos.y,pos.x);					// radian position, relative 0 at 3 o'clock
		// radRel = rad + 0.5pi * dirFlag;		// relative 0 at 12 o'clock, clockwise
		// radRel = (radRel - (startAngle*dirFlag)).wrap(0, 2pi);	// relative to start position
		// if (radRel.inRange(0, sweepLength)) {
		// 	this.inputAction_(radRel/sweepLength); // triggers refresh
		// 	stValue = value;
		// 	stInput = input;
		// };
	}

	direction_ {|dir=\cw|
		direction = dir;
		dirFlag = switch (direction, \cw, {1}, \ccw, {-1});
		this.rangeStartAngle_(rangeStartAngle);
		this.rangeSweepLength_(rangeSweepLength);		// updates prSweepLength
		this.refresh;
	}

	rangeStartAngle_ {|deg=0|
		rangeStartAngle = deg;
		prRangeStartAngle = -0.5pi + rangeCenterOffset.degrad + rangeStartAngle.degrad;		// start angle always relative to 0 is up, cw
	}

	rangeSweepLength_ {|deg=360|
		rangeSweepLength = deg;
		prRangeSweepLength = rangeSweepLength.degrad * dirFlag;
		this.innerRadiusRatio_(innerRadiusRatio); // update innerRadiusRatio in case this was set to 0
	}

	innerRadiusRatio_ {|ratio|
		innerRadiusRatio = if (ratio == 0) {1e-5} {ratio};
		this.refresh
	}

	outerRadiusRatio_ {|ratio|
		outerRadiusRatio = ratio;
		this.refresh
	}
}


SprdRangeLayer : RotaryArcWedgeLayer {
	// define default properties in an Event as a class method
	*properties {
		^(
			show:					true,					// show this layer or not
			style:				\wedge,				// \wedge or \arc: annularWedge or arc
			// note if \arc, the width follows .width, not strokeWidth
			width:				1,						// width of either annularWedge or arc; relative to wedgeWidth
			radius:				1,						// outer edge of the wedge or arc; relative to maxRadius
			fill:		 			true,					// if annularWedge
			fillColor:		Color.gray.alpha_(0.3),
			stroke:				true,
			strokeColor:	Color.gray,
			strokeType:		\around, 			// if style: \wedge; \inside, \outside, or \around
			strokeWidth:	1, 						// if style: \wedge, if < 1, assumed to be a normalized value and changes with view size, else treated as a pixel value
			capStyle:			\round,				// if style: \arc
			joinStyle:	 	0,						// if style: \wedge; 0=flat
		)
	}

	fill {
		this.fillFromLength(view.prRangeStartAngle, view.prRangeSweepLength)
	}
}

SprdSpreadLayer : RotaryArcWedgeLayer {
	*properties {
		^(
			show:					true,					// show this layer or not
			style:				\wedge,				// \wedge or \arc: annularWedge or arc
			// note if \arc, the width follows .width, not strokeWidth
			width:				1,						// width of either annularWedge or arc; relative to wedgeWidth
			radius:				1,						// outer edge of the wedge or arc; relative to maxRadius
			fill:		 			true,					// if annularWedge
			fillColor:		Color.red.alpha_(0.3),
			stroke:				true,
			strokeColor:	Color.gray,
			strokeType:		\around, 			// if style: \wedge; \inside, \outside, or \around
			strokeWidth:	1, 						// if style: \wedge, if < 1, assumed to be a normalized value and changes with view size, else treated as a pixel value
			capStyle:			\round,				// if style: \arc
			joinStyle:	 	0,						// if style: \wedge; 0=flat
		)
	}

	fill {
		this.fillFromLength(
			view.prRangeStartAngle + (view.inputs[3] * view.prRangeSweepLength),
			view.inputs[2] * view.prRangeSweepLength
		)
	}
}

//
SprdHandleLayer : ValueViewLayer {
	*properties {
		^(
			show:					true,					// show this layer or not
			anchor:				1.1,					// relative to outerRadius
			radius:				0.05,					// if < 1, assumed to be a normalized value and changes with view size, else treated as a pixel value
			fill:		 			true,
			fillColor:		Color.blue.alpha_(0.3),
			stroke:				true,
			strokeColor:	Color.black,
			strokeWidth:	0.15, 						// ratio of radius
		)
	}

	fill {
		var d, rho;

		Pen.push;
		d = if (p.radius<1){p.radius*view.outerRadius}{p.radius} * 2;
		Pen.fillColor_(p.fillColor);

		view.handlePnts.do{|pnt|
			Pen.fillOval([0,0,d,d].asRect.center_(pnt))
		};
		Pen.pop;
	}

	stroke {
		var strokeWidth, d, rho;

		Pen.push;
		d = if (p.radius<1){p.radius*view.outerRadius}{p.radius} * 2;
		strokeWidth = if (p.strokeWidth<1){p.strokeWidth*d}{p.strokeWidth};
		Pen.width_(strokeWidth);
		Pen.strokeColor_(p.strokeColor);

		view.handlePnts.do{|pnt|
			Pen.strokeOval([0,0,d,d].asRect.center_(pnt))
		};

		// Pen.translate(view.cen.x, view.cen.y);
		// rho = view.outerRadius * p.anchor;
		//
		// // for [lo, center, hi], do
		// [view.inputs[3], view.inputs[1], view.inputs[4]].do{ |rot|
		// 	Pen.push;
		// 	Pen.rotate(view.prRangeStartAngle + (rot * view.prRangeSweepLength));
		// 	Pen.width_(strokeWidth);
		// 	Pen.strokeColor_(p.strokeColor);
		// 	Pen.strokeOval( [0,0, d,d].asRect.center_(rho@0));
		// 	Pen.pop;
		// };

		Pen.pop;
	}
}

SprdCurvalueLayer : ValueViewLayer {
	*properties {
		^(
		)
	}
}

SprdLabelLayer : ValueViewLayer {
	*properties {
		^(
		)
	}
}