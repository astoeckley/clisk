(ns
  ^{:author "mikera"
    :doc "Special effects. Images can be generated by composing these functions."}  
  clisk.effects
  (:use [clisk node util functions colours patterns textures]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* true)

(defn psychedelic 
  "Psychedelic colour effect"
  ([src & {:keys [noise-scale noise-bands] 
           :or {noise-scale 0.2 noise-bands 2.0}}]
    (adjust-hue (v* noise-bands (scale noise-scale noise)) src)))

(defn monochrome
  "Converts to monochrome"
  ([src]
    (lightness-from-rgb src)))

(defn posterize
  "Posterization effect with discrete colour bands."
  ([src & {:keys [bands] 
           :or {bands 4}}]
    (let [bands (double bands)
          inv-bands (/ 1.0 (dec bands))
          dec-bands (- bands 0.00001)] 
      (warp
        src 
        (v* inv-bands (vfloor (v* dec-bands pos)))))))

(defn shatter 
  "Breaks an image up into sharp irregular fragments defined by a Voronoi map" 
  [src & args]
  (offset (grain (apply voronoi-points args)) src))

(defn radial 
  "Wraps an image around an origin in a radial fashion in the x-y plane"
  [src & {:keys [repeat] 
          :or {repeat 1.0}}]
  (let []
    (warp [(vfrac (v* repeat (vdivide theta TAU))) 
           radius] src)))