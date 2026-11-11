package com.sma.admin.workspace;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;
import com.sma.admin.workspace.internal.service.ShelfService;

@IntegrationTest @AutoConfigureMockMvc
class ShelfTests {
 @Autowired ShelfService shelf; @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;
 @Autowired com.sma.admin.iam.internal.service.PermissionService permissions;
 @Autowired com.sma.admin.iam.internal.service.UserService users;
 private SmaPrincipal actor(long user,long tenant,String portal){return new SmaPrincipal(user,"shelf-user",tenant,portal,null,true,DataAccess.unrestricted(),Set.of(),"UTC");}
 private ShelfService.NoteInput input(String title){return new ShelfService.NoteInput(title,"text","private body",List.of(),"purple",false,null);}
 private ShelfService.Note create(String title){return shelf.create(new ShelfService.CreateNote(UUID.randomUUID().toString(),input(title)));}
 @Test void isolatesOwnersEvenSuperAdminsTenantsPortalsAndProtectsAllMutationPaths(){
  var a=actor(701,8701,"admin");
  var note=Users.as(a,()->create("owner"));
  var board=Users.as(a,()->shelf.addBookmark(new ShelfService.BookmarkInput(0L,"mine")));
  for(var other:List.of(actor(702,8701,"admin"),actor(701,8702,"admin"),actor(701,8701,"other"))){
   Users.as(other,()->{
    assertThat(shelf.notes()).isEmpty();assertThat(shelf.board().bookmarks()).isEmpty();
    assertThatThrownBy(()->shelf.getNote(note.id())).isInstanceOf(BizException.class);
    assertThatThrownBy(()->shelf.update(note.id(),new ShelfService.UpdateNote(note.version(),input("stolen")))).isInstanceOf(BizException.class);
    assertThatThrownBy(()->shelf.transition(note.id(),new ShelfService.Transition(note.version(),"trash"))).isInstanceOf(BizException.class);
    assertThatThrownBy(()->shelf.removeBookmark(board.bookmarks().getFirst().id(),board.version())).isInstanceOf(BizException.class);
    return null;
   });
  }
  Users.as(a,()->{assertThat(shelf.getNote(note.id()).title()).isEqualTo("owner");return null;});
 }
 @Test void noteRequestDeduplicationVersionsChecklistArchiveAndTrashAreDurable(){
  Users.as(actor(711,8711,"admin"),()->{
   var request=new ShelfService.CreateNote(UUID.randomUUID().toString(),input("one"));
   var n=shelf.create(request);assertThat(shelf.create(request).id()).isEqualTo(n.id());
   var changed=new ShelfService.NoteInput("Checklist","checklist","",List.of(new ShelfService.Item("a","Check data",true)),"green",true,0L);
   var next=shelf.update(n.id(),new ShelfService.UpdateNote(n.version(),changed));
   assertThat(next.items().getFirst().done()).isTrue();assertThat(next.relatedTarget().path()).isEqualTo("/");
   assertThatThrownBy(()->shelf.update(n.id(),new ShelfService.UpdateNote(n.version(),input("lost"))))
    .isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(ShelfErrorCode.CONFLICT));
   var archived=shelf.transition(n.id(),new ShelfService.Transition(next.version(),"archived"));
   assertThatThrownBy(()->shelf.update(n.id(),new ShelfService.UpdateNote(archived.version(),input("invalid")))).isInstanceOf(BizException.class);
   var trash=shelf.transition(n.id(),new ShelfService.Transition(archived.version(),"trash"));assertThat(trash.trashedAt()).isNotNull();
   var restored=shelf.transition(n.id(),new ShelfService.Transition(trash.version(),"active"));assertThat(restored.trashedAt()).isNull();
   assertThat(shelf.getNote(n.id()).items()).isEqualTo(changed.items());
   return null;
  });
 }
 @Test void bookmarkDedupeOrderingModeConflictAndDeletedEntryCanBeAddedAgain(){
  Users.as(actor(721,8721,"admin"),()->{
   var b=shelf.addBookmark(new ShelfService.BookmarkInput(0L,"Home"));
   assertThat(shelf.addBookmark(new ShelfService.BookmarkInput(0L,"Other")).bookmarks()).hasSize(1);
   var n=shelf.editBookmark(b.bookmarks().getFirst().id(),new ShelfService.BookmarkEdit(b.version(),"Alias"));
   assertThatThrownBy(()->shelf.mode(new ShelfService.Mode(b.version(),"floating"))).isInstanceOf(BizException.class);
   var mode=shelf.mode(new ShelfService.Mode(n.version(),"floating"));assertThat(mode.mode()).isEqualTo("floating");
   assertThatThrownBy(()->shelf.reorder(new ShelfService.Order(mode.version(),List.of(999L)))).isInstanceOf(BizException.class);
   var ordered=shelf.reorder(new ShelfService.Order(mode.version(),List.of(b.bookmarks().getFirst().id())));
   var empty=shelf.removeBookmark(b.bookmarks().getFirst().id(),ordered.version());assertThat(empty.bookmarks()).isEmpty();
   assertThat(shelf.addBookmark(new ShelfService.BookmarkInput(0L,"Back")).bookmarks()).hasSize(1);
   assertThatThrownBy(()->shelf.addBookmark(new ShelfService.BookmarkInput(999L,"forged"))).isInstanceOf(BizException.class);
   return null;
  });
 }
 @Test void rejectsInvalidPayloadAndExpiresTrashAfterThirtyDays(){
  Users.as(actor(731,8731,"admin"),()->{
   assertThatThrownBy(()->shelf.create(new ShelfService.CreateNote(UUID.randomUUID().toString(),input(" ")))).isInstanceOf(BizException.class);
   var n=create("expire");var trash=shelf.transition(n.id(),new ShelfService.Transition(n.version(),"trash"));
   jdbc.update("UPDATE workspace_note SET trashed_at=? WHERE id=?",java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(31))),n.id());
   assertThat(shelf.notes()).isEmpty();
   assertThatThrownBy(()->shelf.transition(n.id(),new ShelfService.Transition(trash.version(),"active"))).isInstanceOf(BizException.class);
   return null;
  });
 }
 @Test void concurrentEditsHaveExactlyOneWinnerAndNeverSilentlyOverwrite() throws Exception {
  var a=actor(741,8741,"admin");var n=Users.as(a,()->create("initial"));
  try(var pool=Executors.newFixedThreadPool(2)){
   var gate=new CountDownLatch(1);
   var work=new ArrayList<Future<Boolean>>();
   for(int i=0;i<2;i++){int k=i;work.add(pool.submit(()->{gate.await();return Users.as(a,()->{
    try{shelf.update(n.id(),new ShelfService.UpdateNote(n.version(),input("edit"+k)));return true;}
    catch(BizException ex){assertThat(ex.getErrorCode()).isEqualTo(ShelfErrorCode.CONFLICT);return false;}
   });}));}
   gate.countDown();assertThat(List.of(work.get(0).get(30,TimeUnit.SECONDS),work.get(1).get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
  }
 }
 @Test void capsActiveNotesButArchiveFreesCapacity(){
  Users.as(actor(751,8751,"admin"),()->{
   var first=create("first");
   for(int i=1;i<200;i++)create("note"+i);
   assertThatThrownBy(()->create("overflow")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(ShelfErrorCode.LIMIT));
   shelf.transition(first.id(),new ShelfService.Transition(first.version(),"archived"));create("replacement");
   assertThatThrownBy(()->shelf.transition(first.id(),new ShelfService.Transition(first.version()+1,"active"))).isInstanceOf(BizException.class);
   return null;
  });
 }
 @Test void httpRequiresAuthenticationAndNeverCachesPrivateResponses() throws Exception {
  mvc.perform(get("/api/workspace/shelf")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/workspace/shelf/notes")).andExpect(status().isUnauthorized());
  Users.as(actor(761,1,"admin"),()->{
   try{mvc.perform(get("/api/workspace/shelf/notes").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.testSecurityContext())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));}catch(Exception e){throw new RuntimeException(e);}
   return null;
  });
 }
 @Test void authorizedMenusFollowStableIdsRenamesRevocationAndThirtyBookmarkLimit(){
  Long root=Users.as(actor(781,1,"admin"),()->users.create(new com.sma.admin.iam.internal.service.UserService.Create(
   new com.sma.admin.iam.internal.service.UserService.Input("shelf-menu-user","Shelf tester",null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,null),"Q759f02600")).id());
  jdbc.update("UPDATE iam_user SET super_admin=1 WHERE id=?",root);
  Users.as(actor(root,1,"admin"),()->{
   var input=new com.sma.admin.iam.internal.service.PermissionService.Input(0L,"workspace:shelftest:view","Shelf page",null,"MENU","/shelf-test",null,"ROUTE",1,true,false,true,Map.of());
   var menu=permissions.save(null,input);
   var added=shelf.addBookmark(new ShelfService.BookmarkInput(menu.id(),""));
   assertThat(added.bookmarks().getFirst().target().path()).isEqualTo("/shelf-test");
   permissions.save(menu.id(),new com.sma.admin.iam.internal.service.PermissionService.Input(0L,"workspace:shelftest:view","Renamed",null,"MENU","/shelf-moved",null,"ROUTE",1,true,false,true,Map.of()));
   assertThat(shelf.board().bookmarks().getFirst().target().title()).isEqualTo("Renamed");
   permissions.save(menu.id(),new com.sma.admin.iam.internal.service.PermissionService.Input(0L,"workspace:shelftest:view","Renamed",null,"MENU","/shelf-moved",null,"ROUTE",1,false,false,true,Map.of()));
   assertThat(shelf.board().bookmarks().getFirst().target()).isNull();
   assertThatThrownBy(()->shelf.addBookmark(new ShelfService.BookmarkInput(menu.id(),""))).isInstanceOf(BizException.class);
   for(int i=0;i<30;i++)permissions.save(null,new com.sma.admin.iam.internal.service.PermissionService.Input(
    0L,"workspace:shelfcap"+i+":view","Capacity "+i,null,"MENU","/shelf-cap-"+i,null,"ROUTE",100+i,true,false,true,Map.of()));
   var available=shelf.targets().stream().filter(t->!t.id().equals(menu.id())).toList();
   assertThat(available.size()).isGreaterThanOrEqualTo(30);
   for(int i=0;i<29;i++)shelf.addBookmark(new ShelfService.BookmarkInput(available.get(i).id(),""));
   assertThatThrownBy(()->shelf.addBookmark(new ShelfService.BookmarkInput(available.get(29).id(),""))).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(ShelfErrorCode.LIMIT));
   return null;
  });
 }
 @Test void migrationRerunKeepsExistingPersonalAndIamData(){
  Users.as(actor(771,8771,"admin"),()->{
   var n=create("migration checkpoint");
   var usersBefore=jdbc.queryForObject("SELECT COUNT(*) FROM iam_user",Long.class);
   var migrated=org.flywaydb.core.Flyway.configure().dataSource(TestDatabase.URL,TestDatabase.USERNAME,TestDatabase.PASSWORD)
    .locations("classpath:db/migration/workspace").table("flyway_schema_history_workspace").load().migrate();
   assertThat(migrated.migrationsExecuted).isZero();
   assertThat(shelf.getNote(n.id()).title()).isEqualTo("migration checkpoint");
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_user",Long.class)).isEqualTo(usersBefore);
   return null;
  });
 }
}
