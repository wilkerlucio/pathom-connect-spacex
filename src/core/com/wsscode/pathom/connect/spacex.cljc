(ns com.wsscode.pathom.connect.spacex
  (:require [clojure.core.async :refer [timeout <! #?(:clj <!!)]]
            [clojure.string :as str]
            [#?(:clj  com.wsscode.common.async-clj
                :cljs com.wsscode.common.async-cljs)
             :refer [go-catch <? let-chan chan? <?maybe <!maybe go-promise]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.diplomat.http :as p.http]))

;region entity helpers
(defn adapt-key [k]
  (str/replace k #"_" "-"))

(defn set-ns
  "Set the namespace of a keyword"
  [ns kw]
  (keyword ns (adapt-key (name kw))))

(defn set-ns-seq
  "Set the namespace for all keywords in a collection. The collection kind will
  be preserved."
  [ns s]
  (into (empty s) (map #(set-ns ns %)) s))

(defn set-ns-x
  "Set the namespace of a value. If sequence will use set-ns-seq."
  [ns x]
  (if (coll? x)
    (set-ns-seq ns x)
    (set-ns ns x)))

(defn namespaced-keys
  "Set the namespace of all map keys (non recursive)."
  [e ns]
  (reduce-kv
    (fn [x k v]
      (assoc x (set-ns ns k) v))
    {}
    e))

(defn pull-key
  "Pull some key"
  [x key]
  (-> (dissoc x key)
      (merge (get x key))))

(defn pull-namespaced
  "Pull some key, updating the namespaces of it"
  [x key ns]
  (-> (dissoc x key)
      (merge (namespaced-keys (get x key) ns))))
;endregion

(defn update-if [m k f & args]
  (if (contains? m k)
    (apply update m k f args)
    m))

(defn adapt-core [core]
  (-> core
      (namespaced-keys "spacex.core")))

(defn adapt-payload [payload]
  (-> payload
      (namespaced-keys "spacex.payload")
      (pull-namespaced :spacex.payload/orbit-params "spacex.payload.orbit-params")))

(defn adapt-rocket [rocket]
  (-> rocket
      (namespaced-keys "spacex.rocket")
      (pull-namespaced :spacex.rocket/first-stage "spacex.launch.first-stage")
      (update-if :spacex.launch.first-stage/cores #(mapv adapt-core %))
      (pull-namespaced :spacex.rocket/second-stage "spacex.launch.second-stage")
      (update-if :spacex.launch.second-stage/payloads #(mapv adapt-payload %))
      (pull-namespaced :spacex.rocket/fairings "spacex.launch.fairings")))

(defn adapt-launch-pad [launch-pad]
  (-> launch-pad
      (namespaced-keys "spacex.launch-pad")))

(defn adapt-launch [launch]
  (-> launch
      (update :launch_site adapt-launch-pad)
      (update :rocket adapt-rocket)
      (namespaced-keys "spacex.launch")
      (pull-key :spacex.launch/launch-site)
      (pull-key :spacex.launch/rocket)
      (pull-namespaced :spacex.launch/links "spacex.launch.links")
      (pull-namespaced :spacex.launch/telemetry "spacex.launch.telemetry")))

(def launch-out
  [:spacex.launch/details
   :spacex.launch/flight-number
   :spacex.launch/is-tentative
   :spacex.launch/launch-date-local
   :spacex.launch/launch-date-unix
   :spacex.launch/launch-date-utc
   :spacex.launch/launch-success
   :spacex.launch/launch-year
   :spacex.launch/mission-id
   :spacex.launch/mission-name
   :spacex.launch/ships
   :spacex.launch/static-fire-date-unix
   :spacex.launch/static-fire-date-utc
   :spacex.launch/tentative-max-precision
   :spacex.launch/upcoming
   :spacex.launch-pad/site-id
   :spacex.launch-pad/site-name
   :spacex.launch-pad/site-name-long
   :spacex.launch.fairings/recovered
   :spacex.launch.fairings/recovery-attempt
   :spacex.launch.fairings/reused
   :spacex.launch.fairings/ship
   {:spacex.launch.first-stage/cores [:spacex.core/block
                                      :spacex.core/core-serial
                                      :spacex.core/flight
                                      :spacex.core/gridfins
                                      :spacex.core/land-success
                                      :spacex.core/landing-intent
                                      :spacex.core/landing-type
                                      :spacex.core/landing-vehicle
                                      :spacex.core/legs
                                      :spacex.core/reused]}
   :spacex.launch.links/article-link
   :spacex.launch.links/flickr-images
   :spacex.launch.links/mission-patch
   :spacex.launch.links/mission-patch-small
   :spacex.launch.links/video-link
   :spacex.launch.links/wikipedia
   :spacex.launch.second-stage/block
   {:spacex.launch.second-stage/payloads [:spacex.payload/customers
                                          :spacex.payload/manufacturer
                                          :spacex.payload/nationality
                                          :spacex.payload/norad-id
                                          :spacex.payload/orbit
                                          :spacex.payload/payload-id
                                          :spacex.payload/payload-mass-kg
                                          :spacex.payload/payload-mass-lbs
                                          :spacex.payload/payload-type
                                          :spacex.payload/reused
                                          :spacex.payload.orbit-params/apoapsis-km
                                          :spacex.payload.orbit-params/arg-of-pericenter
                                          :spacex.payload.orbit-params/eccentricity
                                          :spacex.payload.orbit-params/epoch
                                          :spacex.payload.orbit-params/inclination-deg
                                          :spacex.payload.orbit-params/lifespan-years
                                          :spacex.payload.orbit-params/longitude
                                          :spacex.payload.orbit-params/mean-anomaly
                                          :spacex.payload.orbit-params/mean-motion
                                          :spacex.payload.orbit-params/periapsis-km
                                          :spacex.payload.orbit-params/period-min
                                          :spacex.payload.orbit-params/raan
                                          :spacex.payload.orbit-params/reference-system
                                          :spacex.payload.orbit-params/regime
                                          :spacex.payload.orbit-params/semi-major-axis-km
                                          :spacex.payload/cap-serial
                                          :spacex.payload/cargo-manifest
                                          :spacex.payload/flight-time-sec
                                          :spacex.payload/mass-returned-kg
                                          :spacex.payload/mass-returned-lbs]}
   :spacex.launch.telemetry/flight-club
   :spacex.rocket/rocket-id
   :spacex.rocket/rocket-name
   :spacex.rocket/rocket-type
   :spacex.launch.links/presskit
   :spacex.launch.links/reddit-campaign
   :spacex.launch.links/reddit-launch
   :spacex.launch.links/reddit-media
   :spacex.launch.links/reddit-recovery])

(pc/defresolver all-launches
  [env _]
  {::pc/output [{:spacex/all-launches launch-out}]}
  (go-catch
    (->> (p.http/request env "https://api.spacexdata.com/v3/launches"
           {::p.http/accept ::p.http/json}) <?maybe
         ::p.http/body
         (mapv adapt-launch)
         (hash-map :spacex/all-launches))))

(pc/defresolver past-launches
  [env _]
  {::pc/output [{:spacex/past-launches launch-out}]}
  (go-catch
    (->> (p.http/request env "https://api.spacexdata.com/v3/launches/past?limit=10"
           {::p.http/accept ::p.http/json}) <?maybe
         ::p.http/body
         (mapv adapt-launch)
         (hash-map :spacex/past-launches))))

(pc/defresolver upcoming-launches
  [env _]
  {::pc/output [{:spacex/upcoming-launches launch-out}]}
  (go-catch
    (->> (p.http/request env "https://api.spacexdata.com/v3/launches/upcoming"
           {::p.http/accept ::p.http/json}) <?maybe
         ::p.http/body
         (mapv adapt-launch)
         (hash-map :spacex/upcoming-launches))))

(pc/defresolver one-launch
  [env {:spacex.launch/keys [flight-number]}]
  {::pc/input     #{:spacex.launch/flight-number}
   ::pc/output    launch-out
   ::pc/transform (pc/transform-auto-batch 10)}
  (go-catch
    (->> (p.http/request env (str "https://api.spacexdata.com/v3/launches/" flight-number)
           {::p.http/accept ::p.http/json}) <?maybe
         ::p.http/body
         adapt-launch)))

(pc/defresolver latest-launch
  [env _]
  {::pc/output [{:spacex/latest-launch launch-out}]}
  (go-catch
    (->> (p.http/request env "https://api.spacexdata.com/v3/launches/latest"
           {::p.http/accept ::p.http/json}) <?maybe
         ::p.http/body
         adapt-launch
         (hash-map :spacex/latest-launch))))

(def resolvers
  [all-launches past-launches upcoming-launches one-launch latest-launch])

(defn spacex-plugin []
  {::pc/register resolvers})

(comment
  (gstr/replaceAll "rocket_type" "_" "-")
  (->> launches
       (mapv adapt-launch)
       (hash-map :spacex/all-launches)
       (pc/data->shape))

  (->> launches
       (mapv :links))

  (-> launches first)

  (-> latest-launch))
