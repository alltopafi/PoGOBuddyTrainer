I want an android app that helps get a Pokemon Go Buddy excited, this takes a series of interactions on a schedule. I think this makes sense as an adroid live notification, They can all have the same title of Pokemon Buddy Trainer. There a  few different types of notifications 

Notification Types:
  Task: requires interaction from the user to tell if the tasks are completed or aborted
  Timer: represents the time between tasks either 15 minutes or 30 minutes and should have a progress bar and time elapsed / total time
  Completed: No action just Congratulations on making buddy happy
  Abort: The user cancelled the flow title: ❌ Pokémon Buddy Guide message: Trainer session was manually aborted! criticalText: Got Away!
  Missed: The Task notification wasn't completed within 5 minutes and should show the same content as the abort

schedule includes 
Step 1: 
  message:
    1. Take a snapshot 📸
    2. Feed all berries 🍓
    3. Play with buddy 🛝
    4. Battle once. ⚔️
  criticalText:
    Task!

Step 2: 
  message:
    Next Step: Snapshot Only 📸
    {{progress bar that elapsed minutes /15m}}
  criticalText:
    number of minutes remaining
  1. Wait 15 minutes

Step 3:
  message:
    Snapshot Only 📸
  criticalText:
    Task!

Step 4: 
  message:
    Next Step: 1 berry 🍓
    {{progress bar that elapsed minutes /15m}}
  criticalText:
    number of minutes remaining
  1. wait 15 minutes

Step 5:
  message:
    1. Take a snapshot 📸
    2. Feed 1 berry 🍓
    3. Play with buddy 🛝
    4. Battle once. ⚔️
  criticalText:
    Task!

Step 6:
  message:
    Next Step 2 berries 🍓
    {{progress bar that elapsed minutes /30m}}
  criticalText:
    number of minutes remaining
  1. Wait 30 minutes

Step 7:
  message:
    1. Take a snapshot 📸
    2. Feed 2 berries 🍓
    3. Play with buddy 🛝
    4. Battle once. ⚔️
  criticalText:
    Task!

Step 8:
  message:
    Next Step: 1 berry 🍓
    {{progress bar that elapsed minutes /30m}}
  criticalText:
    number of minutes remaining
  1. Wait 30 minutes

Step 9:
  message:
    1. Take a snapshot 📸
    2. Feed 1 berry 🍓
    3. Play with buddy 🛝
    4. Battle once. ⚔️
  criticalText:
    Task!

Step 9:
  title: 🎉 Routine Complete!
  Message: Congratulations, another buddy is happy!
  criticalText:
    Completed!
