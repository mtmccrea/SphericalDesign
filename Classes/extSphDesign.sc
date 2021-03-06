/*
	The following methods extend the SphDesign class and are
	stored here to denote that these methods are copied,
	with some modification, from Scott Wilson's port
	(VBAPSpeakerArray) of Ville Pukki's VBAP library in PureData.
	The copied methods pertain to forming a triangulation of points
	which forms a mesh grid suitable for use with VBAP.
	- M. McCrea, DXARTS, University of Washington, mtm5[at]uw.edu


VBAP created by Ville Pukki
This version ported from ver 0.99 PD code by Scott Wilson
Development funded in part by the AHRC http://www.ahrc.ac.uk

Copyright

This software is being provided to you, the licensee, by Ville Pulkki,
under the following license. By obtaining, using and/or copying this
software, you agree that you have read, understood, and will comply
with these terms and conditions: Permission to use, copy, modify and
distribute, including the right to grant others rights to distribute
at any tier, this software and its documentation for any purpose and
without fee or royalty is hereby granted, provided that you agree to
comply with the following copyright notice and statements, including
the disclaimer, and that the same appear on ALL copies of the software
and documentation, including modifications that you make for internal
use or for distribution:

Written by Ville Pulkki 1999
Helsinki University of Technology
and
Unversity of California at Berkeley

*/

+ SphDesign {

	/*
	Selects the loudspeaker triplets, and
	calculates the inversion matrices for each selected triplet.
	A line (connection) is drawn between each loudspeaker. The lines
	denote the sides of the triangles. The triangles should not be
	intersecting. All crossing connections are searched and the
	longer connection is erased. This yields non-intesecting triangles,
	which can be used in panning.
	See theory in:
	Pulkki, V. Lokki, T. "Creating Auditory Displays with Multiple
	Loudspeakers Using VBAP: A Case Study with DIVA Project",
	International Conference on Auditory Displays -98.
	*/
	// Slight refactor of the original choose_ls_triplets method -mtm
	calcTriplets {
		var i1, j1, k1, m, li, table_size;
		var vb1,vb2,tmp_vec; // instances of VBAPSpeaker
		var connections;
		var distance_table;
		var distance_table_i;
		var distance_table_j;
		var distance;
		var step, dict;
		var numPnts;

		numPnts = this.numPoints;
		connections = Array.fill(numPnts, {Array.newClear(numPnts)});

		triplets = nil;
		for(0.0, numPnts -1, {|i|
			for(i+1.0, numPnts -1, {|j|
				for(j+1.0, numPnts -1, {|k|
					if(this.vol_p_side_lgth(i,j,k) > minSideLength, {
						connections[i][j]=1;
						connections[j][i]=1;
						connections[i][k]=1;
						connections[k][i]=1;
						connections[j][k]=1;
						connections[k][j]=1;
						//"i: % j: %, k: %\n".postf(i, j, k);
						triplets = triplets.add([i,j,k]);
					});
				});
			});
		});

		/* calculate distancies between all lss and sorting them */
		distance_table = Array.newClear((numPnts * (numPnts - 1)) / 2);
		distance_table.size.do{|i| distance_table[i] = Dictionary()};

		table_size = ((numPnts - 1) * (numPnts)) / 2;
		step = 0;

		numPnts.do{|i|
			for(i+1, numPnts - 1, { |j|
				if (connections[i][j] == 1) {
					distance = this.vec_angle(points[i],points[j]).abs;
					dict = distance_table[step];
					dict[\d] = distance;
					dict[\i] = i;
					dict[\j] = j;
				} {
					table_size = table_size - 1;
				};
				step = step + 1;
			});
		};

		// sort by distance
		distance_table.sortBy(\d);

		/* disconnecting connections which are crossing shorter ones,
		starting from shortest one and removing all that cross it,
		and proceeding to next shortest */
		table_size.do{ |i|
			var fst_ls, sec_ls;
			fst_ls = distance_table[i][\i];
			sec_ls = distance_table[i][\j];

			if(connections[fst_ls][sec_ls] == 1, {
				numPnts.do{ |j|
					for(j+1.0, numPnts - 1, {|k|
						if( (j!=fst_ls) && (k != sec_ls) && (k!=fst_ls) && (j != sec_ls), {
							if(this.lines_intersect(fst_ls, sec_ls, j,k), {
								connections[j][k] = 0;
								connections[k][j] = 0;
							});
						});
					});
				};
			});
		};

		/* remove triangles which had crossing sides
		with smaller triangles or include loudspeakers */
		triplets = triplets.reject({|set, idx|
			var test;
			i1 = set[0];
			j1 = set[1];
			k1 = set[2];
			test = (connections[i1][j1] == 0) || (connections[i1][k1] == 0) || (connections[j1][k1] == 0)
			|| this.any_ls_inside_triplet(i1,j1,k1);
			test
		});
	}

	vec_angle { |v1, v2|
		/* angle between two loudspeakers */
		var inner;
		inner = ((v1.x*v2.x) + (v1.y*v2.y) + (v1.z*v2.z)) /
		(this.vec_length(v1) * this.vec_length(v2));
		if(inner > 1.0, {inner = 1.0});
		if (inner < -1.0, {inner = -1.0});
		^abs(acos(inner));
	}

	vec_length { |v1|
		/* length of a vector */
		^(sqrt(v1.x.squared + v1.y.squared + v1.z.squared));
	}

	vec_prod {|v1, v2|
		/* vector dot product */
		^((v1.x*v2.x) + (v1.y*v2.y) + (v1.z*v2.z));
	}


	lines_intersect { |i, j, k, l|
		/* checks if two lines intersect on 3D sphere */
		var v1, v2, v3, neg_v3;
		var angle;
		var dist_ij,dist_kl,dist_iv3,dist_jv3,dist_inv3,dist_jnv3;
		var dist_kv3,dist_lv3,dist_knv3,dist_lnv3;

		v1 = this.unq_cross_prod(points[i], points[j]);
		v2 = this.unq_cross_prod(points[k], points[l]);
		v3 = this.unq_cross_prod(v1, v2);

		neg_v3 = Cartesian.new;
		neg_v3.x= 0.0 - v3.x;
		neg_v3.y= 0.0 - v3.y;
		neg_v3.z= 0.0 - v3.z;

		dist_ij = (this.vec_angle(points[i], points[j]));
		dist_kl = (this.vec_angle(points[k], points[l]));
		dist_iv3 = (this.vec_angle(points[i], v3));
		dist_jv3 = (this.vec_angle(v3, points[j]));
		dist_inv3 = (this.vec_angle(points[i], neg_v3));
		dist_jnv3 = (this.vec_angle(neg_v3, points[j]));
		dist_kv3 = (this.vec_angle(points[k], v3));
		dist_lv3 = (this.vec_angle(v3, points[l]));
		dist_knv3 = (this.vec_angle(points[k], neg_v3));
		dist_lnv3 = (this.vec_angle(neg_v3, points[l]));

		/* if one of loudspeakers is close to crossing point, don't do anything */
		if((abs(dist_iv3) <= 0.01) || (abs(dist_jv3) <= 0.01) ||
			(abs(dist_kv3) <= 0.01) || (abs(dist_lv3) <= 0.01) ||
			(abs(dist_inv3) <= 0.01) || (abs(dist_jnv3) <= 0.01) ||
			(abs(dist_knv3) <= 0.01) || (abs(dist_lnv3) <= 0.01), {^false});

		/* if crossing point is on line between both loudspeakers return 1 */
		if (((abs(dist_ij - (dist_iv3 + dist_jv3)) <= 0.01 ) &&
			(abs(dist_kl - (dist_kv3 + dist_lv3))  <= 0.01)) ||
		((abs(dist_ij - (dist_inv3 + dist_jnv3)) <= 0.01)  &&
			(abs(dist_kl - (dist_knv3 + dist_lnv3)) <= 0.01 )), { ^true}, {^false});

	}

	/* vector cross product */
	unq_cross_prod { |v1, v2|
		var length, result;
		result = Cartesian.new;
		result.x = (v1.y * v2.z ) - (v1.z * v2.y);
		result.y = (v1.z * v2.x ) - (v1.x * v2.z);
		result.z = (v1.x * v2.y ) - (v1.y * v2.x);
		length = this.vec_length(result);
		result.x = result.x / length;
		result.y = result.y / length;
		result.z = result.z / length;
		^result;
	}

	/* calculate volume of the parallelepiped defined by the loudspeaker
	direction vectors and divide it with total length of the triangle sides.
	This is used when removing too narrow triangles. */
	vol_p_side_lgth { |i, j, k|
		var volper, lgth;
		var xprod;

		xprod = this.unq_cross_prod(points[i], points[j]);
		volper = abs(this.vec_prod(xprod, points[k]));
		lgth = (abs(this.vec_angle(points[i], points[j]))
			+ abs(this.vec_angle(points[i], points[k]))
			+ abs(this.vec_angle(points[j], points[k])));
		if(lgth > 0.00001, { ^(volper / lgth)}, { ^0.0 });
	}

	any_ls_inside_triplet { |a, b, c|
		/* returns true if there is loudspeaker(s) inside given ls triplet */
		var invdet;
		var lp1, lp2, lp3;
		var invmx;
		var tmp;
		var any_ls_inside, this_inside;

		lp1 =  points[a];
		lp2 =  points[b];
		lp3 =  points[c];

		invmx = Array.newClear(9);

		/* matrix inversion */
		invdet = 1.0 / (  lp1.x * ((lp2.y * lp3.z) - (lp2.z * lp3.y))
			- (lp1.y * ((lp2.x * lp3.z) - (lp2.z * lp3.x)))
			+ (lp1.z * ((lp2.x * lp3.y) - (lp2.y * lp3.x))));

		invmx[0] = ((lp2.y * lp3.z) - (lp2.z * lp3.y)) * invdet;
		invmx[3] = ((lp1.y * lp3.z) - (lp1.z * lp3.y)) * invdet.neg;
		invmx[6] = ((lp1.y * lp2.z) - (lp1.z * lp2.y)) * invdet;
		invmx[1] = ((lp2.x * lp3.z) - (lp2.z * lp3.x)) * invdet.neg;
		invmx[4] = ((lp1.x * lp3.z) - (lp1.z * lp3.x)) * invdet;
		invmx[7] = ((lp1.x * lp2.z) - (lp1.z * lp2.x)) * invdet.neg;
		invmx[2] = ((lp2.x * lp3.y) - (lp2.y * lp3.x)) * invdet;
		invmx[5] = ((lp1.x * lp3.y) - (lp1.y * lp3.x)) * invdet.neg;
		invmx[8] = ((lp1.x * lp2.y) - (lp1.y * lp2.x)) * invdet;

		any_ls_inside = false;
		for(0, this.numPoints - 1, {|i|
			if((i != a) && (i != b) && (i != c), {
				this_inside = true;
				for(0, 2, {|j|
					tmp = points[i].x * invmx[0 + (j*3)];
					tmp = points[i].y * invmx[1 + (j*3)] + tmp;
					tmp = points[i].z * invmx[2 + (j*3)] + tmp;
					if(tmp < -0.001, {this_inside = false;});
				});
				if(this_inside, {any_ls_inside = true});
			});
		});
		^any_ls_inside;
	}
}
