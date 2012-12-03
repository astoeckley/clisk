(ns
  ^{:author "mikera"
    :doc "Functions for clisk image synthesis. Images should be generated by composing these functions."}  
  clisk.functions
  (:import clisk.Util)
  (:import java.lang.Math)
  (:import [java.awt.image BufferedImage BufferedImageOp] )
  (:import clisk.Maths)
  (:use [clisk node util])
  (:use [clojure.tools macro]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* true)

(def ^:const TAU (* 2.0 Math/PI))
(def ^:const PI (* 0.5 TAU))

(def ^:const POSITION-SYMBOLS
  "A vector of position symbols."
  ['x 'y 'z 't])

(def ^:const C-SYMBOLS
  "A vector of position symbols."
  ['c0 'c1 'c2 'c3 'c4 'c5])

(def ^:const pos 
  "A special node that evaluates to the current position in space as a 4D vector."
  (code-node POSITION-SYMBOLS))

(def ^:const c 
  "A special node that evaluates to the c vector. Used as initial fractal position"
  (code-node C-SYMBOLS))

;; alias key private functions from clisk.node
(def dimensions #'clisk.node/dimensions)
(def vectorize #'clisk.node/vectorize)
(def component #'clisk.node/component)
(def components #'clisk.node/components)
(def take-components #'clisk.node/take-components)
(def texture-map #'clisk.node/texture-map)
(def evaluate #'clisk.node/evaluate)
(def warp #'clisk.node/warp)

(defn ensure-scalar [x]
  "Ensure x is a scalar value. If x is a vector, resturns the first component (index 0)."
  (let [x (node x)]
	  (cond 
	    (vector-node? x)
	      (component 0 x)
	    (scalar-node? x)
	      x
	    :else x)))

(defn x 
  "Extracts the x component of a position vector"
  ([v]
	  (component 0 v)))

(defn y 
  "Extracts the y component of a position vector"
  ([v]
	  (component 1 v)))

(defn z 
  "Extracts the z component of a position vector"
  ([v]
    (component 2 v)))

(defn t 
  "Extracts the t component of a position vector"
  ([v]
    (component 3 v)))

(defn alpha 
  "Extracts the alpha component of a colour vector. Assumes 1.0 if not present."
  ([v]
    (if (> (dimensions v) 3) (component 3 v) 1.0)))

(defn rgb
  "Creates an RGB colour vector"
  ([^java.awt.Color java-colour]
    (rgb (/ (.getRed java-colour) 255.0)
         (/ (.getGreen java-colour) 255.0)
         (/ (.getBlue java-colour) 255.0)))
  ([r g b]
    [r g b])
  ([r g b a]
    (rgb r g b)))

(defn rgba
  "Creates an RGBA colour vector"
  ([^java.awt.Color java-colour]
    (rgba (/(.getRed java-colour) 255.0)
          (/(.getBlue java-colour) 255.0)
          (/(.getGreen java-colour) 255.0)
          (/(.getAlpha java-colour) 255.0)))
  ([r g b]
    (rgba r g b 1.0))
  ([r g b a]
    [r g b a]))


(defn check-dims [& vectors]
  "Ensure that parameters are equal sized vectors. Returns the size of the vector(s) if successful."
  (let [vectors (map node vectors)]
    (let [[v & vs] vectors
          dims (dimensions v)]
      (if (every? #(= dims (dimensions %)) vs)
        dims
        (error "Unequal vector sizes: " (map count vectors))))))

(defn vconcat 
  "Concatenate a set of vectors into a longer vector. Treats scalars as 1D vectors." 
  [& vectors]
  (vec-node (mapcat (comp :nodes vectorize) vectors)))

;; note that vectorize-op optimises for zero and identity elements
(defn vectorize-op 
  "Make an arbitrary function work on clisk vectors in a component-wise manner"
  ([f & {:keys [zero identity unary-identity]}]
	  (fn [& vs]
	    (let [vs (map node vs)
	          dims (apply max (map dimensions vs))]
        (cond 
          (and unary-identity (= 1 (count vs)))
            (first vs)
          (some vector-node? vs)
			      (apply vector-node 
		          (for [i (range dims)]
			          (apply function-node f (map #(component i %) vs))))
          (and zero (some (is-constant zero) vs))
            ZERO-NODE
          identity
            (let [vs (cons (first vs) 
                           (filter (complement (is-constant identity)) (rest vs)))]
              (if (== 1 (count vs))
                (first vs)
                (apply function-node f vs)))
          :else
            (apply function-node f vs))))))


(defmacro vlet 
  "let one or more values within a vector function" 
  [bindings form]
  (let [bind-pairs (partition 2 bindings)
        symbols (map first bind-pairs)
        values (map second bind-pairs)
        gensyms (map #(gensym (str "alias-" (name %))) symbols)
        quoted-gensyms (map #(do `(quote ~%)) gensyms)
        bindings (interleave quoted-gensyms values)]
    (if-not (even? (count bindings)) (error "vlet requires an even number of binding forms"))
    `(let [~@(interleave symbols quoted-gensyms)]
       (#'clisk.node/vlet* [~@bindings] ~form)))) 

(defmacro let-vector 
  "let a vector value into each component of a function" 
  [bindings form]
  (let [[symbol value & more] bindings]
    ;;(print (str (merge {} vector-value))) 
    `(let [vector-value# (vectorize ~value)
           components# (:nodes vector-value#) 
           gensyms# (vec (for [i# (range (count components#))] 
                           (gensym (str "comp" i#))))
           vector-node# (node gensyms#)
           ~symbol vector-node#]
       (#'clisk.node/vlet* 
         (vec 
           (let []
             (interleave gensyms# components#))) 
         ~(if (empty? more) 
            form
            `(let-vector [~@more] ~form)))))) 


(defn vif [condition a b]
  "Conditional vector function. First scalar argument is used as conditional value, > 0.0  is true."
  (let [a (node a)
        b (node b)
        condition (component 0 condition)
        adims (dimensions a)
        bdims (dimensions b)
        dims (max adims bdims)]
    (transform-components
       (fn [c a b]
         (if (:constant c)
           ;; constant case - use appropriate branch directly
           (if (> (evaluate c) 0.0 ) a b) 
           ;; variable case
           `(if (> ~(:code c) 0.0 ) ~(:code a) ~(:code b)) ))
       condition
       a
       b)))


(defn apply-to-components
  "Applies a function f to all components of a vector"
  ([f v]
    (let [v (vectorize v)]
      (apply function-node f (:nodes v)))))

(defn ^:static frac
  "Retuns the fractional part of a number. Equivalent to Math/floor."
  (^double [^double x]
    (- x (Math/floor x))))

(defn ^:static square-function
  "Retuns the square of a number."
  (^double [^double x]
    (* x x)))

(defn ^:static phash 
  "Returns a hashed double value in the range [0..1)"
  (^double [^double x]
    (Util/dhash x))
  (^double [^double x ^double y]
    (Util/dhash x y))
  (^double [^double x ^double y ^double z]
    (Util/dhash x y z))
  (^double [^double x ^double y ^double z ^double t]
    (Util/dhash x y z t)))

(def vsin
  (vectorize-op 'Math/sin))

(def vcos
  (vectorize-op 'Math/cos))

(def vabs
  (vectorize-op 'Math/abs))

(def vround
  (vectorize-op 'Math/round))

(def vfloor
  (vectorize-op 'Math/floor))

(def vfrac
  (vectorize-op 'clisk.functions/frac))

(def square
  (vectorize-op 'clisk.functions/square-function))

(def step 
  "A step function that works on both vectors and scalars"
  vfloor)

(def v+ 
  "Adds two or more vectors"
  (vectorize-op 'clojure.core/+ :identity 0.0 :unary-identity true))

(def v* 
  "Multiplies two or more vectors"
  (vectorize-op 'clojure.core/* :zero 0.0 :identity 1.0 :unary-identity true))

(def v- 
  "Subtracts two or more vectors"
  (vectorize-op 'clojure.core/- :identity 0.0))

(def vdivide 
  "Divides two or more vectors"
  (vectorize-op 'clojure.core// :identity 1.0))

(def vpow 
  "Raises a vector to an exponent"
  (vectorize-op 'Math/pow :identity 1.0))

(def vmod
  "Returns the modulus of a vector by component."
  (vectorize-op 'clisk.Maths/mod))

(def vsqrt
  "Takes the square root of a value"
  (vectorize-op 'Math/sqrt))

(def sigmoid
  "Sigmoid function on a scalar or vector in range [0..1]"
  (vectorize-op 'clisk.Maths/sigmoid))

(def triangle-wave
  "Triangular wave function in range [0..1]"
  (vectorize-op 'clisk.Maths/t))

(defn dot 
	"Returns the dot product of two vectors"
  ([a b]
	  (let [a (vectorize a)
	        b (vectorize b)]
     (apply v+ (map v* (:nodes a) (:nodes b))))))

(defn cross3
  "Returns the cross product of 2 3D vectors"
  ([a b]
    (transform-node
	    (fn [a b]
	       (let [[x1 y1 z1] (:codes (vectorize a))
		          [x2 y2 z2] (:codes (vectorize b))]
		        [`(- (* ~y1 ~z2) (* ~z1 ~y2))
		         `(- (* ~z1 ~x2) (* ~x1 ~z1))
		         `(- (* ~x1 ~y2) (* ~y1 ~x1))]))
     (node a)
     (node b))))

(defn max-component 
  "Returns the max component of a vector"
  ([v]
    (let [v (vectorize v)]
      (transform-node 
        (fn [v]
          `(max ~@(:codes v)))
        v))))

(defn min-component 
  "Returns the min component of a vector"
  ([v]
    (let [v (vectorize v)]
      (transform-node 
        (fn [v]
          `(min ~@(:codes v)))
        v))))

(defn length 
  "Calculates the length of a vector"
  ([a]
	  (let [comps (:nodes (vectorize a))]
	    (apply transform-node
            (fn [& comps]
              (node `(Math/sqrt (+ ~@(map #(do `(let [v# (double ~%)] (* v# v#))) (map :code comps))))))
            comps))))

(defn normalize 
  "Normalizes a vector"
  ([a]
	  (let-vector [x a]
               (vdivide x (length x)))))



(defn compose 
  "Composes two or more vector functions"
  ([f g]
    (warp g f))
  ([f g & more]
    (compose f (apply compose g more)))) 

(defn rotate
  "Rotates a function in the (x,y plane)"
  ([angle function]
    (transform-components
	     (fn [s c node]
	        `(let [xt# (- (* ~(:code c) ~'x) (* ~(:code s) ~'y)) 
                 yt# (+ (* ~(:code s) ~'x) (* ~(:code c) ~'y)) 
                 ~'x xt#
                 ~'y yt#]
            ~(:code node)))
       (vsin angle)
       (vcos angle)
	     function)))

(defn scale 
  "Scales a function by a given factor."
  ([factor f] 
	  (let [factor (node factor)
	        f (node f)]
	    (warp (vdivide position-symbol-vector factor) f))))

(defn offset 
  "Offsets a function by a specified amount"
  ([offset f]
    (warp (v+ 
             position-symbol-vector
             (vectorize offset))
           f)))

(defn matrix 
  "Creates a matrix multiplication function"
  ([rows]
	  (if (= 1 (count rows))
	    (dot (first rows) pos)
	    (vec-node
	      (map-indexed (fn [i row]
	                     (dot row pos))
	                   rows)))))

(defn matrix-transform 
  "Performs a matrix transformation on the given source"
  ([matrix-rows]
    (fn [src]
      (matrix-transform matrix-rows src)))
  ([matrix-rows src]
    (warp (matrix matrix-rows) src)))

(defn affine-transform 
  "Performs an affine transformation (implicitly appends a 1 as final row of the input vector)"
  ([matrix-rows]
    (fn [src]
      (affine-transform matrix-rows src)))
  ([matrix-rows src]
    (let [dims (dec (apply max (map count matrix-rows)))]
      (warp ((matrix matrix-rows) (vconcat (take-components dims pos) [1.0])) 
            src))))

(def ^:private 
      offsets-for-vectors (vec(map node[[-120.34 +340.21 -13.67 +56.78]
						                            [+12.301 +70.261 -167.678 +34.568]
						                            [+78.676 -178.678 -79.612 -80.111]
						                            [-78.678 7.6789 200.567 124.099]])))

(defn vector-offsets [func]
  "Creates a vector version of a scalar function, where the components are offset versions of the original scalar function"
  (let [func (node func)] 
    (if-not (scalar-node? func) (error "vector-offsets requires a scalar function"))
    (vec-node 
	    (map 
	      (fn [off]
	        (offset off func))
        offsets-for-vectors))))

(defn gradient 
  "Computes the gradient of a scalar function f with respect to [x y z t]"
	([f]
	  (let [epsilon 0.000001
	        f (component 0 f)]
	    (transform-components 
        (fn [f pos] 
          (let [sym (:code pos)]
	          `(clojure.core// 
	             (clojure.core/-
	               (let [~sym (clojure.core/+ ~epsilon ~sym)]
	                 ~(:code f))
	               ~(:code f))
	             ~epsilon)))
	      f
        (node position-symbol-vector)))))

(defn ^:static scalar-lerp 
  "Performs clamped linear interpolation between two values, according to the proportion given in the 3rd parameter."
  (^double [^double proportion ^double a ^double b]
	  (let [a# a
	        b# b
	        v# proportion]
	     (if (<= v# 0) a#
	       (if (>= v# 1) b#
	         (+ 
	           (* v# b#)
	           (* (- 1.0 v#) a#)))))))

(def lerp 
  "Performs clamped linear interpolation between two vectors, according to the proportion given in the 3rd parameter."
  (vectorize-op `scalar-lerp))
  

(defn colour-map 
  "Creates a colour map function using a set of value-colour mappings"
  ([mapping]
    (fn [x]
      (colour-map mapping x)))
  ([mapping x]
		(vlet [v (component 0 x)] 
      (let [vals (vec mapping)
		        c (count vals)]
		    (cond 
		      (<= c 0) (error "No colour map available!")
		      (== c 1) (node (second (vals 0)))
		      (== c 2) 
		        (let [lo (first (vals 0))
		              hi (first (vals 1))]
                (vif 
                  (v- hi lo)
                  (lerp  ;; normal case interpolation with positive range
			              (vdivide (v- v lo) (v- hi lo))
                    (node (second (vals 0))) 
			              (node (second (vals 1))))      
                  (node (second (vals 0)))   ;; degenerate case with zero or less range
			            ))
		      :else
		        (let [mid (quot c 2)
		              mv (first (vals mid))
		              upper (colour-map (subvec vals mid c) v)
		              lower (colour-map (subvec vals 0 (inc mid)) v)]
		          (vif (v- v mv)
	              upper
	              lower)))))))

(defn image-filter 
  "Applies a BufferedImageOp filter to a source image or function"
  [filter source 
   & {:keys [width height size]
      :or {width (or size clisk.node/DEFAULT-IMAGE-WIDTH)
           height (or size clisk.node/DEFAULT-IMAGE-HEIGHT)}}]
  (let [^java.awt.image.BufferedImageOp filter (if (symbol? filter) (eval `(new ~filter)) filter)
        ^java.awt.image.BufferedImage source-img (if (instance? java.awt.image.BufferedImage source) source (img source width height)) 
        dest-img (.createCompatibleDestImage filter source-img (.getColorModel source-img))]
    (.filter filter source-img dest-img)
    (texture-map dest-img)))

(def scalar-hash-function
  "Hash function producing a scalar value in the range [0..1) for every 
   unique point in space"
  `(phash ~'x ~'y ~'z ~'t))

(def vector-hash
  "Hash function producing a vector value 
   in the range [0..1)^4 for every 
   unique point in space"
  (vector-offsets scalar-hash-function))

(def vmin
  "Computes the maximum of two vectors"
  (vectorize-op 'Math/min))

(def vmax
  "Computes the maximum of two vectors"
  (vectorize-op 'Math/max))

(defn clamp [v low high]
  "Clamps a vector between a low and high vector. Typically used to limit 
   a vector to a range e.g. (vclamp something [0 0 0] [1 1 1])."
  (let [v (vectorize v)
        low (vectorize low)
        high (vectorize high)]
    (vmax low (vmin high v))))

(defn average [& vs]
  "Returns the average of several arguments"
  (let [n (count vs)]
    (v* (/ 1.0 n) (apply v+ vs))) )

;; polar co-ordinate functions

(defn theta 
  "Returns the angle of a vector in polar co-ordinates"
  ([v]
    (transform-node
      (fn [x y]
        `(+ Math/PI (Math/atan2 ~(:code y) ~(:code x))))
      (component 0 v)
      (component 1 v))) )


(defn radius 
  "Returns the raidus of a vector in polar co-ordinates"
  ([v]
    (transform-node
      (fn [x y]
        `(let [x# ~(:code x) y# ~(:code y)] (Math/sqrt (+ (* x# x#) (* y# y#)) ) ))
      (component 0 v)
      (component 1 v))) )

(defn polar
  "Returns the polar co-ordinates of a vector"
  ([v]
    (let-vector [v v]
      (vector-node (radius v) (theta v)))))

(defn viewport 
  "Rescales the texture as if viwed from [ax, ay] to [bx ,by]"
  ([a b function]
    (let [[x1 y1] a
          [x2 y2] b
          w (double (- x2 x1))
          h (double (- y2 y1))]
      (scale 
        [(/ 1.0 w) (/ 1.0 h) 1.0 1.0] 
        (offset [(double x1) (double y1)] function)))))

(defn seamless 
  "Creates a seamless 2D tileable version of a 4D texture in the [0 0] to [1 1] region. The scale argument detrmines the amount of the source texture to be used per repeat."
  ([v4]
    (seamless 1.0 v4))
  ([scale v4]
    (let [v4 (node v4)
          scale-factor (double (/ 1.0 scale TAU))
          dims (dimensions v4)]
      (warp
        [`(* (Math/cos (* ~'x TAU)) ~scale-factor) 
         `(* (Math/sin (* ~'x TAU)) ~scale-factor) 
         `(* (Math/cos (* ~'y TAU)) ~scale-factor)
         `(* (Math/sin (* ~'y TAU)) ~scale-factor)]
        v4))))

(defn height 
  "Calculates the height value (z) of a source function"
  ([f] 
    (z f)))

(defn height-normal 
  "Calculates a vector normal to the surface defined by the z-value of a source vector or a scalar height value. The result is *not* normalised."
  ([heightmap]
    (v- [0 0 1] (components [0 1] (gradient (z heightmap)))))
  ([scale heightmap]
    (v- [0 0 1] (components [0 1] (gradient (v* scale (z heightmap)))))))


(defn light-value 
  "Calculates diffuse light intensity given a light direction and a surface normal vector. 
   This function performs its own normalisation, so neither the light vector nor the normal vector need to be normalised."
  ([light-direction normal-direction]
      (vmax 0.0 
	        (dot (normalize light-direction) (normalize normal-direction)))))

(defn diffuse-light 
  "Calculate the diffuse light on a surface normal vector.
   This function performs its own normalisation, so neither the light vector nor the normal vector need to be normalised."
  ([light-colour light-direction normal-direction]
    (v* light-colour (light-value light-direction normal-direction))))

(defn render-lit
  "Renders example lighting on a couloured surface with a given heightmap function"
  ([height]
    (render-lit [1.0 1.0 1.0] height))
  ([colour height]
    (v* colour
      (v+ 
        0.2 
        (diffuse-light 0.8 [-1.0 -1.0 1.0] (height-normal (v* 0.1 height )))))))

(defmacro limited-loop [limit [& bindings] form]
  `(let [~'*recur-limit* (long ~limit)]
     (double 
       (loop [~@(concat bindings ['i 0])]
         ~form))))

(defmacro limited-recur [bailout-result & values]
  `(if (< ~'i ~'*recur-limit*) 
     (recur ~@values (inc ~'i))
     ~bailout-result))

(defn vloop [init rest & {:keys [max-iterations]
                          :or {max-iterations 10}}]
  "Creates a vector loop construct"
  (let [init (vectorize init)
        n (dimensions init)
        rest (node rest)]
    (apply transform-components
      (fn [rest & inits]
        `(limited-loop ~max-iterations 
                       [~@(mapcat list (take n POSITION-SYMBOLS) (map :code inits))]
           ~(:code rest)))
      (cons
        rest
        (:nodes init)))))

(defn vfor [init while update & {:keys [max-iterations result bailout-result]
                                 :or {max-iterations 10}}]
  "Creates a vector for-loop construct"
  (let [init (vectorize init)
        n (dimensions init)
        update (take-components n update)
        result (or result (node result))
        bailout-result (or bailout-result result)]
	  (#'clisk.node/vlet* (interleave (take n C-SYMBOLS) (:nodes init))
      (vloop
		    (take-components n c)
		    (vif 
		      while
		      (apply transform-components
	              (fn [bailout-result-comp & comps]
	                `(limited-recur ~(:code bailout-result-comp) ~@(map :code comps)))
	              bailout-result
	              (:nodes update))
		      result)
		    :max-iterations max-iterations))))

;; ==========================================================
;; Fractal generation function

(defn fractal [& {:keys [init while update result bailout-result max-iterations]
                  :or {init pos}}]
   (if-not update (error "fractal requires a :update clause"))
   (vfor init 
         (or while 1) 
         update 
         :result (or result pos)
         :bailout-result (or bailout-result 0.0)
         :max-iterations max-iterations))
