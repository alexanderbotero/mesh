package com.gentics.cailun.demo.verticle;

import static io.vertx.core.http.HttpMethod.GET;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.ext.apex.core.Session;

import java.util.Arrays;

import org.jacpfx.vertx.spring.SpringVerticle;
import org.neo4j.graphdb.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gentics.cailun.core.AbstractCailunRestVerticle;
import com.gentics.cailun.core.repository.GenericNodeRepository;
import com.gentics.cailun.core.repository.GroupRepository;
import com.gentics.cailun.core.repository.PageRepository;
import com.gentics.cailun.core.repository.PermissionRepository;
import com.gentics.cailun.core.repository.RoleRepository;
import com.gentics.cailun.core.repository.TagRepository;
import com.gentics.cailun.core.repository.UserRepository;
import com.gentics.cailun.core.rest.model.GenericNode;
import com.gentics.cailun.core.rest.model.Page;
import com.gentics.cailun.core.rest.model.Tag;
import com.gentics.cailun.core.rest.model.auth.Group;
import com.gentics.cailun.core.rest.model.auth.Role;
import com.gentics.cailun.core.rest.model.auth.User;
import com.gentics.cailun.core.rest.model.auth.basic.BasicPermission;
import com.gentics.cailun.etc.CaiLunSpringConfiguration;
import com.gentics.cailun.etc.Neo4jSpringConfiguration;

/**
 * Dummy verticle that is used to setup basic demo data
 * 
 * @author johannes2
 *
 */
@Component
@Scope("singleton")
@SpringVerticle
public class CustomerVerticle extends AbstractCailunRestVerticle {

	private static Logger log = LoggerFactory.getLogger(CustomerVerticle.class);

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PermissionRepository permissionRepository;

	@Autowired
	private PageRepository pageRepository;

	@Autowired
	private TagRepository tagRepository;

	@Autowired
	private GroupRepository groupRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private CaiLunSpringConfiguration cailunConfig;

	@Autowired
	private GenericNodeRepository genericRepository;

	@Autowired
	private Neo4jSpringConfiguration neo4jSpringConfiguration;

	public CustomerVerticle() {
		super("page");
	}

	@Override
	public void start() throws Exception {
		super.start();

		addPermissionTestHandler();

		// Users
		User john = new User("joe1");
		john.setFirstname("John");
		john.setLastname("Doe");
		john.setEmailAddress("j.doe@gentics.com");
		john.setPasswordHash(cailunConfig.passwordEncoder().encode("test123"));

		User mary = new User("mary2");
		mary.setFirstname("Mary");
		mary.setLastname("Doe");
		mary.setEmailAddress("m.doe@gentics.com");
		mary.setPasswordHash(cailunConfig.passwordEncoder().encode("lalala"));
		userRepository.save(Arrays.asList(john, mary));

		// Roles
		Role adminRole = new Role("admin role");
		roleRepository.save(adminRole);
		Role guestRole = new Role("guest role");
		roleRepository.save(guestRole);

		// Groups
		Group rootGroup = new Group("superusers");
		rootGroup.getMembers().add(john);
		rootGroup.getRoles().add(adminRole);

		groupRepository.save(rootGroup);
		Group guests = new Group("guests");
		guests.getParents().add(rootGroup);
		guests.getMembers().add(mary);
		guests.getRoles().add(guestRole);
		groupRepository.save(guests);

		// Content
		Tag rootTag = new Tag("/");
		rootTag.tag("home").tag("jotschi");
		rootTag.tag("root");
		rootTag.tag("var").tag("www");
		Tag wwwTag = rootTag.tag("var").tag("www");
		wwwTag.tag("site");
		Tag postsTag = wwwTag.tag("posts");
		Tag blogsTag = wwwTag.tag("blogs");
		tagRepository.save(rootTag);

		Page rootPage = new Page("rootPage");
		rootPage.setContent("This is root");
		rootPage.setFilename("index.html");
		rootPage.setTeaser("Yo root");
		rootPage.tag(rootTag);
		pageRepository.save(rootPage);

		for (int i = 0; i < 6; i++) {
			Page page = new Page("Hallo Welt");
			page.setFilename("some" + i + ".html");
			page.setContent("some content");
			page.tag(blogsTag);
			pageRepository.save(page);

		}

		for (int i = 0; i < 3; i++) {
			Page page = new Page("Hallo Welt");
			page.setFilename("some_posts" + i + ".html");
			page.setContent("some content");
			page.tag(postsTag);
			pageRepository.save(page);

		}
		Page page = new Page("New BlogPost");
		page.tag(blogsTag);
		page.setFilename("blog.html");
		page.setContent("This is the blogpost content");
		page.setAuthor("Jotschi");
		page.setTeaser("Jo this page is the second blogpost");
		pageRepository.save(page);

		page = new Page("Hallo Cailun");
		page.setFilename("some2.html");
		page.setContent("some more content");
		page.tag(postsTag);
		pageRepository.save(page);

		Page indexPage = new Page("Index With Perm");
		indexPage.setFilename("index.html");
		indexPage.setContent("The index page<br/><a href=\"${Page(10)}\">Link</a>");
		indexPage.setTitle("Index Title");
		indexPage.setAuthor("Jotschi");
		indexPage.setTeaser("Yo guckste hier");
		indexPage.tag(wwwTag);

		indexPage.linkTo(page);
		pageRepository.save(indexPage);

		try (Transaction tx = neo4jSpringConfiguration.getGraphDatabaseService().beginTx()) {
			// Add admin permissions to all pages
			int i = 0;
			for (GenericNode currentNode : genericRepository.findAll()) {
//				if (i % 2 == 0) {
					log.info("Adding BasicPermission to node {" + currentNode.getId() + "}");
					BasicPermission permission = new BasicPermission(adminRole, currentNode);
					permission.setRead(true);
					permission.setCreate(true);
					permission.setDelete(true);
					permissionRepository.save(permission);
//				} else {
//					log.info("Adding CustomPermission to node {" + currentNode.getId() + "}");
//					CustomPermission customPerm = new CustomPermission(adminRole, currentNode);
//					customPerm.setCustomActionAllowed(true);
//					permissionRepository.save(customPerm);
//				}
				i++;
			}
			tx.success();
		}

	}

	private void addPermissionTestHandler() {
		route("/permtest").method(GET).handler(rh -> {
			Session session = rh.session();
			Page page = pageRepository.findOne(23L);
			boolean perm = getAuthService().hasPermission(session.getPrincipal(), new CustomShiroGraphPermission(page));
			rh.response().end("User perm for node {" + page.getId() + "} : " + (perm ? "jow" : "noe"));
		});

	}

}
