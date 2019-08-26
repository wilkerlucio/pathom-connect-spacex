(ns com.wsscode.pathom.connect.spacex.workspaces-main
  (:require [cljs.core.async :as async :refer [go <!]]
            [com.wsscode.common.async-cljs :refer [<? go-catch]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [nubank.workspaces.model :as wsm]
            [nubank.workspaces.core :as ws]
            [com.wsscode.pathom.viz.workspaces :as pvw]
            [com.wsscode.pathom.connect.spacex :as spacex]
            [com.wsscode.pathom.diplomat.http :as p.http]
            [com.wsscode.pathom.diplomat.http.fetch :as p.http.fetch]
            [nubank.workspaces.lib.local-storage :as ls]))


(def parser
  (p/parallel-parser
    {::p/env     (fn [env]
                     (merge
                       {::p/reader               [p/map-reader pc/parallel-reader pc/ident-reader p/env-placeholder-reader]
                        ::p/placeholder-prefixes #{">"}
                        ::p.http/driver          p.http.fetch/request-async}
                       env))
     ::p/mutate  pc/mutate-async
     ::p/plugins [p/error-handler-plugin
                  p/request-cache-plugin
                  p/trace-plugin
                  (pc/connect-plugin)
                  (spacex/spacex-plugin)]}))

(ws/defcard spacex-api-parser-demo
            (pvw/pathom-card {::pvw/parser #'parser}))

(ws/mount)
