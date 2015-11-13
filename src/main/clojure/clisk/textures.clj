(ns clisk.textures
  (:use [clisk core functions patterns colours util node]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)



(def agate
  "A monochrome agate-style rock texture"
  (scale 0.3 
		(offset 
		  (v* 4 plasma)
		  (colour-map [[0 0] [0.05 0.5] [0.5 1.0] [0.95 0.5] [1.0 0]] vfrac))))

(def clouds
  "A cloudlike texture"
  (scale 0.3
     (v- 1 (vpow plasma 3)) ))


(def velvet
  "A nice velvetly pattern."
  (warp (sigmoid (v* 2 vsnoise)) (scale 0.2 noise)))

(def flecks
  "Stranges wispy flecks"
  (scale 0.1 (v* 2.0 (apply-to-components `min vnoise))))

(def wood
  "Spherical wood-like texture centred at origin"
  (scale 0.1 (colour-map [[0 0] [1 1]] (vfrac length))))

(def cannon (texture-map "Cannon.jpg"))

(def clojure (texture-map "Clojure_300x300.png"))