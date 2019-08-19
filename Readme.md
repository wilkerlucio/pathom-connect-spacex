## SpaceX API access

This project was used in the Dutch Clojure Days 2018 for demonstrations of [`Pathom`](https://github.com/wilkerlucio/pathom) for implementing `Graph APIs` for the `YouTube API`.

## Usage

- Install the dependencies 

```
yarn 
```
- To start the build 

```
yarn start
```

- Go to the `http://localhost:8087/` and when the build completes you'll see the workspace.


- Try out a query like so 

```clojure
  [{:spacex/all-launches
    [:spacex.launch/flight-number
     :spacex.launch.links/video-link]}]
``` 

