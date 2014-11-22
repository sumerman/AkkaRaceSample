AkkaRaceSample
==============

A sample app that demonstrates the utmost importance of the become + Stash approach in some cases.

## Disclaimer

I'm neither scala nor akka developer and this code was written solely to support my point in a [discussion (in Russian)](http://eax.me/akka-deadlock/#comment-1701021163).

## Background
In a nutshell, the author of the blog has claimed two things:
  - In erlang one can easily end up in a deadlock, by simply making two processes to `gen_sever:call` each other (true, but benign, and easily discoverable). 
  - In akka such a situation is almost impossible because ask pattern does not block a caller.
  
My points were:
  - ask pattern + pipeTo is a rough equivalent for:
  
  ``` erlang
  handle_call({some_message, Arg}, From, State) ->
    spawn_link(fun() ->
      {ok, Res} = gen_server:call(OtherPid, {this_takes_a_long_time, Arg}),
      gen_server:reply(From, {ok, build_a_res(Res)})
    end),
    {noreply, State}.
  ```
  - sometimes one needs to block to preserve correctness (e.g. by utilizing `become` and `Stash`)
  and then one can easily end up in a deadlock and there are no miracles even in akka.

## This app

Is just a quickly coined example to support my claim that sometimes 
to block is the only correct (or at least the only one that 
[obviously has no deficiencies](http://en.wikiquote.org/wiki/C._A._R._Hoare#The_Emperor.27s_Old_Clothes)) 
way to do a thing.

### Output

On my machine the app gives an output like following:

```
Result1: Some(DataObject{ key: 'foo'; value: 'banana' })
Result2: Some(DataObject{ key: 'foo'; value: 'bar' })
========================================
Res: OK
Res: Error(Failure(sample.race.KV$PutVersionMismatch$: Version mismatch on put))
Get: Data(Some(DataObject{ key: 'bar'; value: '1' }))
--------------
Res: OK
Res: Error(Failure(sample.race.KV$PutVersionMismatch$: Version mismatch on put))
Get: Data(Some(DataObject{ key: 'bar'; value: '1' }))
--------------
Res: OK
Res: Error(Failure(sample.race.KV$PutVersionMismatch$: Version mismatch on put))
Get: Data(Some(DataObject{ key: 'bar'; value: '1' }))
=======================================
Res: OK
Res: Error(sample.race.KV$PutVersionMismatch$: Version mismatch on put)
Get: Data(Some(DataObject{ key: 'bar'; value: '1' }))
--------------
Res: OK
Res: OK
Get: Data(Some(DataObject{ key: 'bar'; value: '2' }))
--------------
Res: OK
Res: OK
Get: Data(Some(DataObject{ key: 'bar'; value: '2' }))
```
  
